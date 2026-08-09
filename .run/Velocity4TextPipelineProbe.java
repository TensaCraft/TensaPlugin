import ua.co.tensa.text.TextPipeline;

/** Launched by smoke-velocity.ps1 against the built plugin and Velocity JARs. */
public final class Velocity4TextPipelineProbe {
    private Velocity4TextPipelineProbe() {
    }

    public static void main(String[] arguments) {
        String url = "https://minecraft-ua.com/minecraft/aeronautics";
        TextPipeline.Rendered rendered = TextPipeline.operator(
                "<click:open_url:'" + url + "'><aqua>" + url + "</aqua></click>"
        );
        if (rendered.clickableUrls() != 1) {
            throw new IllegalStateException("Expected exactly one OPEN_URL component");
        }
        System.out.println("VELOCITY_TEXT_PIPELINE_COMPATIBLE");
    }
}
