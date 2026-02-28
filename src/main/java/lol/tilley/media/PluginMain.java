package lol.tilley.media;

import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.Variant;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.plugin.Plugin;
import org.rusherhack.client.api.utils.ChatUtils;

import java.io.IOException;
import java.util.Map;

public class PluginMain extends Plugin {

	@Override
	public void onLoad() {
		this.getLogger().info("Plugin rusherhack-media-controls loaded");
		RusherHackAPI.getCommandManager().registerFeature(new SongCommand());
		RusherHackAPI.getHudManager().registerFeature(new MediaHudElement());
		ChatUtils.print(System.getenv("DBUS_SESSION_BUS_ADDRESS"));
		ChatUtils.print(getSong());
	}

	@Override
	public void onUnload() {
		this.getLogger().info("Plugin rusherhack-media-controls unloaded!");
	}


	private String getSong() {
		try (DBusConnection conn = DBusConnectionBuilder.forSessionBus().build()) {

			Properties props = conn.getRemoteObject(
					"org.mpris.MediaPlayer2.spotify",
					"/org/mpris/MediaPlayer2",
					Properties.class
			);

			Variant<?> metadata = props.Get(
					"org.mpris.MediaPlayer2.Player",
					"Metadata"
			);

			Map<String, Variant<?>> map = (Map<String, Variant<?>>) metadata.getValue();
			String title = (String) map.get("xesam:title").getValue();
			String artist = ((java.util.List<String>) map.get("xesam:artist").getValue()).get(0);
			return artist + " - " + title;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}