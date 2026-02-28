package lol.tilley.media;

import org.rusherhack.client.api.feature.command.Command;
import org.rusherhack.core.command.annotations.CommandExecutor;

public class SongCommand extends Command {

	public SongCommand() {
		super("song", "description");
	}
	

	@CommandExecutor
	private String song() {
		return "command";
	}
	
}
