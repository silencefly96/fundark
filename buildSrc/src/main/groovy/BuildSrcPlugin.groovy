import org.gradle.api.Plugin
import org.gradle.api.Project

public class BuildSrcPlugin implements Plugin<Project>{

    @Override
    void apply(Project project) {
        println("BuildSrcPlugin")
    }
}