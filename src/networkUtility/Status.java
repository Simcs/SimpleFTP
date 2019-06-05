package networkUtility;

import java.util.HashMap;
import java.util.Map;

public enum Status {
	
	OK(100, "OK"),
	PARAMETER_NOT_A_DIRECTORY(-100, "paramter is not a directory"),
	INVALID_DIRETORY_NAME(-101, "directory name is invalid"),
	TOO_FEW_ARGUMENTS(-200, "too few arguments for command"),
	FILE_NOT_EXISTS(-300, "file not exists"),
	UNKNOWN_ERROR(-400, "unkonwn reason");
	
	private static Map<Integer, Status> statusByCode = new HashMap<>();
	static {
		for(Status s : Status.values())
			statusByCode.put(s.statusCode, s);
	}

	public static Status getStatusByCode(int code) {
		return statusByCode.get(code);
	}
	
	private int statusCode;
	private String message;

	private Status(int statusCode, String msg) {
		this.statusCode = statusCode;
		this.message = msg;
	}
	
	// negative status means error
	public boolean isError() {
		return statusCode < 0;
	}
	
	public int getStatusCode() {
		return statusCode;
	}
	
	public String getMessage() {
		return message;
	}
	
	@Override
	public String toString() {
		return "code : " + statusCode + " message : " + message;
	}
	
}
