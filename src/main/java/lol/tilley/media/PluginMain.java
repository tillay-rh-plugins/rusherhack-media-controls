package lol.tilley.media;

import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.plugin.Plugin;

public class PluginMain extends Plugin {

	@Override
	public void onLoad() {
		this.getLogger().info("Plugin rusherhack-media-controls loaded");
		RusherHackAPI.getWindowManager().registerFeature(new MediaWindow());
	}

	@Override
	public void onUnload() {
		this.getLogger().info("Plugin rusherhack-media-controls unloaded!");
	}
}