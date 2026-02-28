package lol.tilley.media;


import org.rusherhack.client.api.feature.hud.TextHudElement;
import org.rusherhack.core.utils.Timer;

public class MediaHudElement extends TextHudElement {
    private final Timer timer = new Timer();

    public MediaHudElement() {
        super("MediaHudElement");
    }

    @Override
    public String getText () {
        if (timer.passed(1000)) {
            timer.reset();
        }
        return "";
    }
}
