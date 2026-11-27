package com.campus.career.holland.profile;

import com.campus.career.common.BusinessException;
import com.campus.career.common.ErrorCode;
import com.campus.career.holland.HollandScorer;
import com.campus.career.holland.QuizRepository;
import com.campus.career.holland.profile.dto.ProfileView;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 画像生成的编排层，对应 architecture 4.2.3 的 profile 部分、4.2.4 与 4.2.5。
 *
 * <p>只做「取三类输入 → 交给合成器 → 存回」的串联，规则本身在 {@link ProfileSynthesizer}。
 *
 * <h2>三类输入</h2>
 * <ol>
 *   <li>问卷作答（通过 {@link QuizRepository} 的题号映射重新计分，同时拿到可追溯明细）
 *   <li>已确认档案（{@link ResumeProfileSource}）
 *   <li>求职意向（{@link StudentIntentRepository}）
 * </ol>
 * 后两类目前都是内存占位，见各自接口的说明。
 */
@Service
public class ProfileService {

    private final QuizRepository quizRepository;
    private final ResumeProfileSource resumeProfileSource;
    private final StudentIntentRepository intentRepository;
    private final ProfileRepository profileRepository;
    private final ProfileSynthesizer synthesizer;

    public ProfileService(QuizRepository quizRepository,
                          ResumeProfileSource resumeProfileSource,
                          StudentIntentRepository intentRepository,
                          ProfileRepository profileRepository,
                          ProfileSynthesizer synthesizer) {
        this.quizRepository = quizRepository;
        this.resumeProfileSource = resumeProfileSource;
        this.intentRepository = intentRepository;
        this.profileRepository = profileRepository;
        this.synthesizer = synthesizer;
    }

    /** 读取当前画像；尚未生成时为空，由上层转成 {@code "data": null}（4.2.4，空态不是错误）。 */
    public Optional<ProfileView> current(String studentId) {
        return profileRepository.find(studentId);
    }

    /**
     * 重新生成画像（4.2.5、US-3、AC-3）。
     *
     * <p>问卷没提交就要求生成画像，按边界行 8 返回 422 与 {@code QUIZ_INCOMPLETE}。
     * 这里用通用业务异常而不是 {@code QuizIncompleteException}：那个异常带「未答题号」，
     * 而本场景并不缺题，是根本没提交过，硬套会返回一个语义不对的空 {@code missing} 数组。
     */
    public ProfileView regenerate(String studentId) {
        if (!quizRepository.isSubmitted(studentId)) {
            throw new BusinessException(ErrorCode.QUIZ_INCOMPLETE,
                    "问卷尚未提交，无法生成画像");
        }
        return generate(studentId);
    }

    /**
     * 重新计分并生成画像。
     *
     * <p>重新计分而不是复用库里的分数，是为了同时拿到 trace —— AC-8 的追溯校验需要它，
     * 而分数本身只是 trace 的汇总结果，重新算一遍比多存一份明细更省事，
     * 也避免两份数据日后走岔。
     */
    public ProfileView generate(String studentId) {
        HollandScorer.Result result = HollandScorer.score(quizRepository.findAnswers(studentId));
        return generateFrom(studentId, result);
    }

    /**
     * 用已算好的计分结果生成画像，供提交链路复用，避免同一个请求里算两遍。
     *
     * <p>写入用 {@link ProfileRepository#replace}，整体替换旧画像（AC-3、FR-7）。
     */
    public ProfileView generateFrom(String studentId, HollandScorer.Result result) {
        ProfileView profile = synthesizer.synthesize(
                result,
                resumeProfileSource.findConfirmed(studentId),
                intentRepository.find(studentId));
        profileRepository.replace(studentId, profile, result.scores());
        return profile;
    }
}
