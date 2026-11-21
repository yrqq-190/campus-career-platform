package com.campus.career.holland;

import com.campus.career.common.BusinessException;
import com.campus.career.common.ErrorCode;
import com.campus.career.holland.dto.QuestionView;
import com.campus.career.holland.dto.QuestionsResponse;
import com.campus.career.holland.dto.SaveAnswerResponse;
import com.campus.career.holland.dto.SubmitResponse;
import com.campus.career.holland.profile.ProfileService;
import com.campus.career.holland.profile.ScoreNotTraceableException;
import com.campus.career.holland.profile.dto.ProfileView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * M2 问卷的作答、续答、提交校验与计分。
 *
 * <p>对应 spec/module-02 的 FR-2（作答保存）、FR-3（提交校验）、FR-4（计分与追溯），
 * 接口契约见 architecture 4.2.1 ～ 4.2.3。计分属服务层（architecture 第 2 章已明确），
 * API 层只做参数与路由。
 *
 * <p>提交成功后接着调 {@link ProfileService} 合成画像，一次请求交齐 scores 与 profile，
 * 与 4.2.3 的响应结构一致。画像规则本身不在这个类里。
 */
@Service
public class QuizService {

    private final QuizRepository repository;
    private final ProfileService profileService;

    public QuizService(QuizRepository repository, ProfileService profileService) {
        this.repository = repository;
        this.profileService = profileService;
    }

    /** 取 30 题与已保存作答，支撑中途退出后的断点续答（AC-4、4.2.1）。 */
    public QuestionsResponse questions(String studentId) {
        Map<Integer, Integer> answers = repository.findAnswers(studentId);
        List<QuestionView> views = new ArrayList<>(HollandQuestionBank.QUESTION_COUNT);
        for (HollandQuestion question : HollandQuestionBank.all()) {
            views.add(QuestionView.of(question));
        }
        return new QuestionsResponse(views, answers, answers.size(), repository.isSubmitted(studentId));
    }

    /**
     * 单题即时保存（FR-2）。
     *
     * <p>每次选择立刻落库，不依赖「保存草稿」按钮。问卷提交后作答只读，
     * 再次修改按契约 4.2.2 失败表返回 409 与 {@code QUIZ_DUPLICATE_SUBMIT}。
     */
    public SaveAnswerResponse saveAnswer(String studentId, int questionId, int option) {
        boolean saved = repository.saveAnswerIfNotSubmitted(studentId, questionId, option);
        if (!saved) {
            throw new BusinessException(ErrorCode.QUIZ_DUPLICATE_SUBMIT,
                    "问卷已提交，不能再修改作答");
        }
        return new SaveAnswerResponse(true, repository.findAnswers(studentId).size());
    }

    /**
     * 提交问卷、计分并生成画像（AC-1、AC-6、AC-7，以及 4.2.3 的 profile）。
     *
     * <p>判定顺序与理由：
     * <ol>
     *   <li>先查完整性再抢提交标记。若顺序反过来，一个未答完的问卷会把「已提交」标记占掉，
     *       后面用户补完题再提交就会收到 409，而实际上一份画像都没生成；
     *   <li>完整性用「1–30 里缺哪些」算，不比对 answeredCount，因为作答 Map 可能因并发写入
     *       出现题号错位，逐个查才准；
     *   <li><b>追溯校验放在抢标记之前</b>。若先标了已提交再发现分数不可追溯，
     *       学生会卡在「已提交但永远没有画像」的状态，重新提交又只会拿到 409；
     *   <li>计分是纯计算不会失败，同样放在抢标记之前；
     *   <li>标记用原子的 markSubmittedIfAbsent，两人同时点提交时只有一个能拿到 true（AC-7）。
     * </ol>
     */
    public SubmitResponse submit(String studentId) {
        Map<Integer, Integer> answers = repository.findAnswers(studentId);

        List<Integer> missing = new ArrayList<>();
        for (int id = 1; id <= HollandQuestionBank.QUESTION_COUNT; id++) {
            if (!answers.containsKey(id)) {
                missing.add(id);
            }
        }
        if (!missing.isEmpty()) {
            // 未答完不写任何画像数据，已答题目原样保留（AC-6、边界行 1）
            throw new QuizIncompleteException(missing.size() + " 题未作答", missing);
        }

        HollandScorer.Result result = HollandScorer.score(answers);
        if (!result.allTraceable()) {
            // AC-8：数据异常就不让它占掉已提交状态，学生重新提交还能救
            throw new ScoreNotTraceableException("六维得分无法完整回溯到原始作答，本次提交不生效");
        }

        if (!repository.markSubmittedIfAbsent(studentId)) {
            throw new BusinessException(ErrorCode.QUIZ_DUPLICATE_SUBMIT,
                    "问卷已提交，请勿重复提交");
        }
        repository.saveScores(studentId, result.scores());

        ProfileView profile = profileService.generateFrom(studentId, result);
        return new SubmitResponse(result.scores(), profile);
    }

    /** 供测试与后续任务使用，拿到可追溯的计分明细，不对外暴露。 */
    public HollandScorer.Result scoringDetail(String studentId) {
        return HollandScorer.score(repository.findAnswers(studentId));
    }
}
