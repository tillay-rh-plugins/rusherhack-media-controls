package lol.tilley.media;

import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.plugin.Plugin;

import java.io.IOException;

public class PluginMain extends Plugin {

	@Override
	public void onLoad() {
		this.getLogger().info("Plugin rusherhack-media-controls loaded");
        try {
            RusherHackAPI.getHudManager().registerFeature(new SpotifyHudElement(this));
        } catch (IOException e) {
            this.getLogger().error("failed to load media controls", e);
        }
    }

	@Override
	public void onUnload() {
		this.getLogger().info("Plugin rusherhack-media-controls unloaded!");
	}
}