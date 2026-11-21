package com.campus.career.holland;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * {@link QuizRepository} 的内存实现，仅为 T1 建表合并前的占位。
 *
 * <p><b>限制</b>：进程重启后作答全部丢失，也不支持多实例部署。
 * 这不是「凑合」而是明确的范围约定：本周依赖未完成，不写落库代码，
 * 只保证契约与业务判定能跑通、能被测试覆盖。表合并后换成 JDBC 实现即生效。
 *
 * <p>线程安全：外层用 ConcurrentHashMap 定位学生，单学生的状态加锁保护，
 * 让「重复提交只接受第一次」在并发下也成立（AC-7 的 S1/S2 并发场景）。
 */
@Repository
public class InMemoryQuizRepository implements QuizRepository {

    private final Map<String, StudentState> store = new ConcurrentHashMap<>();

    @Override
    public Map<Integer, Integer> findAnswers(String studentId) {
        StudentState state = store.get(studentId);
        if (state == null) {
            return Map.of();
        }
        synchronized (state) {
            return Map.copyOf(state.answers);
        }
    }

    @Override
    public boolean saveAnswerIfNotSubmitted(String studentId, int questionId, int option) {
        StudentState state = store.computeIfAbsent(studentId, key -> new StudentState());
        synchronized (state) {
            if (state.submitted) {
                return false;
            }
            state.answers.put(questionId, option);
            return true;
        }
    }

    @Override
    public boolean isSubmitted(String studentId) {
        StudentState state = store.get(studentId);
        if (state == null) {
            return false;
        }
        synchronized (state) {
            return state.submitted;
        }
    }

    @Override
    public boolean markSubmittedIfAbsent(String studentId) {
        StudentState state = store.computeIfAbsent(studentId, key -> new StudentState());
        synchronized (state) {
            if (state.submitted) {
                return false;
            }
            state.submitted = true;
            return true;
        }
    }

    @Override
    public Map<HollandDimension, Integer> findScores(String studentId) {
        StudentState state = store.get(studentId);
        if (state == null || state.scores == null) {
            return Map.of();
        }
        synchronized (state) {
            return Collections.unmodifiableMap(new EnumMap<>(state.scores));
        }
    }

    @Override
    public void saveScores(String studentId, Map<HollandDimension, Integer> scores) {
        StudentState state = store.computeIfAbsent(studentId, key -> new StudentState());
        synchronized (state) {
            state.scores = new EnumMap<>(scores);
        }
    }

    /** 单个学生的作答状态。所有读写都在持有该对象监视器时进行。 */
    private static final class StudentState {
        private final Map<Integer, Integer> answers = new HashMap<>();
        private boolean submitted;
        private Map<HollandDimension, Integer> scores;
    }
}
