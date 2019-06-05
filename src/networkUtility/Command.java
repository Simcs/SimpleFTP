package networkUtility;

import java.util.HashMap;
import java.util.Map;

public enum Command {
	CHANGE_DIRECTORY("CD"),
	LIST("LIST"),
	GET("GET"),
	PUT("PUT");
	
	private static Map<String, Command> cmdByString = new HashMap<>();
	static {
		for(Command c : values())
			cmdByString.put(c.cmd, c);
	}

	public static Command getCommandByString(String cmd) {
		return cmdByString.get(cmd.toUpperCase());
	}
	
	private String cmd;
	
	private Command(String cmd) {
		this.cmd = cmd;
	}
	
	public String getCommand() {
		return cmd;
	}
	
}
