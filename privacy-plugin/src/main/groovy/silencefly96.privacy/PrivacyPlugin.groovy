package silencefly96.privacy


import org.gradle.api.Plugin
import org.gradle.api.Project

public class PrivacyPlugin implements Plugin<Project>{

    @Override
    void apply(Project project) {
        println("配置成功--------->PrivacyPlugin")
        project.android.registerTransform(new PrivacyTransform())
    }
}