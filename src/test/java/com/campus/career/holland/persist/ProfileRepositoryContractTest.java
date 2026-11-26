package com.campus.career.holland.persist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.campus.career.holland.profile.ProfileRepository;
import com.campus.career.holland.profile.dto.DirectionView;
import com.campus.career.holland.profile.dto.ProfileView;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ProfileRepository} 的存储契约，两个实现（内存 / JDBC）都必须原样通过。
 *
 * <h2>这里固定的是 AC-3 的结构保证</h2>
 * 接口只有一个写入方法 {@code replace}，没有 insert / update 之分，所以本类刻意只测两件事：
 * 「读得到刚写的那份」和「写两次只剩后一份」。只要这两条成立，
 * 「同一学生两份画像并存」或「改到一半」这两种错误状态就无法通过类型系统写出来。
 *
 * <p>不测「画像内容怎么算」——那是 {@code ProfileSynthesizer} 的职责，已有
 * {@code ProfileSynthesizerTest} 覆盖 AC-2、AC-5、AC-8。存储层只管存取。
 */
abstract class ProfileRepositoryContractTest {

    /** 造一个刚创建、没有任何画像的实现。 */
    protected abstract ProfileRepository newRepository();

    /** 一份最小但四项齐全的画像，用来验证存取往返。 */
    private static ProfileView sample(String directionName, String city) {
        return new ProfileView(
                List.of(new DirectionView(directionName, "依据 R 与 I 两个维度的得分")),
                List.of(city),
                List.of("互联网"),
                List.of());
    }

    @Test
    @DisplayName("未生成画像时 find 返回 empty，不返回 null")
    void absentProfileIsEmptyOptional() {
        assertTrue(newRepository().find("profile-empty").isEmpty(),
                "未建档要返回 Optional.empty()。返回 null 会把判空责任推给每个调用方");
    }

    @Test
    @DisplayName("replace 之后再 find，读到的内容与写入的一致")
    void replaceThenFindRoundTrip() {
        ProfileRepository repository = newRepository();
        ProfileView expected = sample("前端开发", "杭州");
        repository.replace("profile-one", expected);

        ProfileView actual = repository.find("profile-one").orElseThrow();
        assertEquals(expected, actual, "四项内容必须原样读回，不能丢字段或改顺序");
    }

    @Test
    @DisplayName("AC-3：replace 两次只保留后一份，不并存两份")
    void replaceKeepsOnlyLatest() {
        ProfileRepository repository = newRepository();
        repository.replace("profile-replace", sample("前端开发", "杭州"));
        repository.replace("profile-replace", sample("后端开发", "苏州"));

        ProfileView latest = repository.find("profile-replace").orElseThrow();
        assertEquals("后端开发", latest.directions().get(0).name(),
                "读到的必须是第二次写入的内容，AC-3 要求新画像整体替换旧的");
        assertEquals(List.of("苏州"), latest.cities(),
                "城市也要跟着换。若出现新旧字段混合，说明实现做成了逐字段更新而不是整体替换");
    }

    @Test
    @DisplayName("两个学生的画像互不串")
    void profilesAreIsolatedByStudent() {
        ProfileRepository repository = newRepository();
        repository.replace("profile-a", sample("前端开发", "杭州"));
        repository.replace("profile-b", sample("数据分析", "成都"));

        assertEquals("前端开发", repository.find("profile-a").orElseThrow().directions().get(0).name());
        assertEquals("数据分析", repository.find("profile-b").orElseThrow().directions().get(0).name());
    }

    @Test
    @DisplayName("一个学生有画像不影响另一个学生仍是空态")
    void oneStudentDoesNotCreateAnother() {
        ProfileRepository repository = newRepository();
        repository.replace("profile-has", sample("前端开发", "杭州"));

        assertFalse(repository.find("profile-none").isPresent(),
                "A 建了画像，B 仍应是 empty。若这里读到东西，说明存储键用错了");
    }
}
