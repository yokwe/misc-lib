package yokwe.util.http;

public interface Download {
	public void setRequesterBuilder(RequesterBuilder requesterBuilder);
	
	public void addTask(Task task);
	
	public void addHeader(String name, String value);
	public void setReferer(String value);
	public void setUserAgent(String value);
	
	public void setThreadCount(int newValue);
	
	public void startProcessTask();
	public void waitProcessTask();
	public void showRunCount();
	default void startAndWait() {
		startProcessTask();
		waitProcessTask();
	}
}	
