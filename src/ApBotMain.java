public class ApBotMain {

	public static void main(String[] args) {
		args = new String[1];
		args[0] = "apb.cfg";
		// construct and initialize
		ApBotClient ap = new ApBotClient(args[0]);
		// run
		ap.run();
	}
}
