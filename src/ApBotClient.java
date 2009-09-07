import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.jscape.inet.email.EmailMessage;
import com.jscape.inet.http.HttpCookie;
import com.jscape.inet.http.HttpException;
import com.jscape.inet.http.HttpParameter;
import com.jscape.inet.http.HttpRequest;
import com.jscape.inet.http.HttpResponse;
import com.jscape.inet.https.Https;
import com.jscape.inet.mime.MimeException;
import com.jscape.inet.smtp.Smtp;
import com.jscape.inet.smtp.SmtpException;
import com.jscape.inet.smtpssl.SmtpSsl;

@SuppressWarnings("unchecked")
public class ApBotClient {
	
    // configuration file settings, don't touch!
	private String username;
	private String password;
	private String botName;
	private int frequency;
	private boolean hideUnbookable;
	private boolean mailEnable;
	private boolean ssl;
	private String smtpServer;
	private int smtpPort;
	private String smtpUser;
	private String smtpPass;
	private String sendTo;
	private String cc;
	private String mailFrom;
	private String loginUrl;
	private String apartmentUrl;
	private String scriptSmAgentName;
	private String scriptTarget;
	private String blacklistFile;
	private boolean silentMode;
	private String pollingPage;
	private boolean verbose;
	
    // session temporary values
    private HttpCookie aspSession = null;
    private HttpCookie smSession = null;
    
    /* program code */
    
    // constructor
	public ApBotClient(String cfgFile) {
		try {
			// initialize all user settings
			ConfigReader conf = new ConfigReader(cfgFile, 128);
			username = conf.readKey("username");
			password = conf.readKey("password");
			// misc
			botName = conf.readKey("bot_name");
			frequency = Integer.parseInt(conf.readKey("frequency"));
			blacklistFile = conf.readKey("blacklist_file");
			silentMode = Boolean.parseBoolean(conf.readKey("silent_mode"));
			verbose = Boolean.parseBoolean(conf.readKey("verbose_mode"));
			// polling settings
			hideUnbookable  = Boolean.parseBoolean(conf.readKey("hide_unbookable"));
			// emailing
			mailEnable = Boolean.parseBoolean(conf.readKey("mail_enable"));
			ssl = Boolean.parseBoolean(conf.readKey("ssl"));
			smtpServer = conf.readKey("smtp_server");
			smtpPort = Integer.parseInt(conf.readKey("smtp_port"));
			smtpUser = conf.readKey("smtp_user");
			smtpPass = conf.readKey("smtp_pass");
			sendTo = conf.readKey("mail_sendTo");
			cc = conf.readKey("mail_cc");
			mailFrom = conf.readKey("mail_from");
			// url-related
			loginUrl = conf.readKey("login_url");
			scriptTarget = conf.readKey("script_target");
			scriptSmAgentName = conf.readKey("script_smagent_name");
			pollingPage = conf.readKey("polling_page");
			apartmentUrl = conf.readKey("apartment_url");			
		} catch (Exception e) {
			errorPrint("FATAL ERROR! Configuration file was not found/could not be read.");
			//e.printStackTrace();
			System.exit(0);
		} 
	}
	
	private void errorPrint(String output) {
		if (silentMode == false) {
			System.err.println(output);
		} // else do nothing
	}
	
	private void condPrint(String output) {
		if (silentMode == false) {
			System.out.print(output);
		} // else do nothing
	}
    
    private ArrayList<String> filter(String fileName, ArrayList<String> arr) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(new File(fileName)));
		String lr = null;
		
		while ((br.read()) == 35); // skip commented(35 = #) rows
			while ((lr = br.readLine()) != null) {
				if (lr == "") {
					br.readLine();	// skip empty rows
				}
				else {
					for (int i = 0; i < arr.size(); i++) {
						if (lr.equals(arr.get(i) )) {
							arr.remove(i); // remove blacklisted dnr number
					}
				}
			}
		}
		return arr;
	}

	private String getTagValue(String tagName, String body) throws IOException {
		int QUOTE_SIGN = 34;
		String valueField = "value=";
		// construct file reading objects
		StringBuilder sb = new StringBuilder();
		StringReader r = new StringReader(body);
		int chr; // stores one char
		boolean foundName = false;
	
		while ((chr = r.read()) != 0 && ((char)chr < Character.MAX_VALUE) ) { // while we have not reached EOF
			sb = new StringBuilder(); // reset if we had to start over
			int i = 0;
			while (foundName == false && i < tagName.length() && (char)chr == tagName.charAt(i)) {
				sb.append((char)chr);
				i++;
				chr = r.read();	// fetch next char
			}
			if ( sb.toString().equals(tagName) || foundName == true ) { // if we found what we are searching for
				foundName = true; // set this so we can remember
				sb = new StringBuilder();	// reset sb now that we set foundName
				int j = 0;
				while ((j < valueField.length()) && (char)chr == valueField.charAt(j) ) {
					sb.append((char)chr);
					j++;
					chr = r.read(); // fetch next char
				}
				if ( sb.toString().equals(valueField) ) { // if value is imminent
					//r.read(); // there is a " in the way, remove it
						sb = new StringBuilder();	// reset sb
					while ((chr = r.read()) != QUOTE_SIGN) {	// " signifies the end of the value field
						sb.append((char)chr);
					} // when we have reached here, we got the whole value stored in sb
					return sb.toString();	// return it
					}
			}
		}
		return null; // return null to signify error
	}
	

	private void appendToBlacklist(String dnrToBlacklist) throws IOException {
		// blacklist this dnr so that we won't register it in the future
		// read the old file
		BufferedReader r = new BufferedReader(new FileReader(blacklistFile));
		StringBuilder sb = new StringBuilder();
		String result;
		
		while ((result = r.readLine()) != null) { // until we reach the end of the file
			sb.append(result+"\n"); // append all lines in the file
		}
		r.close(); // we are done so close this stream
		
		// write old + new blacklisted dnr's
		BufferedWriter w = new BufferedWriter(new FileWriter(blacklistFile));
		sb.append(dnrToBlacklist);		// add our new blacklisted dnr
		w.write(sb.toString());			// write back
		w.close();	// finish
	}

	private void sendMail(String URL, String output) {
		if (mailEnable == true) { // if mailing is enabled in the config file
			Smtp smtp; // establish connection
			if (ssl == true) {
				smtp = new SmtpSsl(smtpServer, smtpPort);
			}
			else {
				smtp = new Smtp(smtpServer);
			}
			try {
				smtp.connect();
				smtp.login(smtpUser, smtpPass);
			} catch (SmtpException e) {
				errorPrint("ERROR! Problem with SMTP credentials/connection.");
			}
			// build message
			try {
				EmailMessage message = new EmailMessage();
				message.setTo(sendTo);
				message.setCc(cc);
				message.setFrom(mailFrom);
				message.setSubject("Email from ABot");
				message.setBody("Hi! \n\nThis is an automated message.\n\nAn apartment with the following address has been registered;\n"+ URL 
						+ "\n\nPlease note that you should not have more than two currently active registrations for me to be able to do my job.\n\nGenerated event message: \n" + output + "\n\nBest regards,\n" + botName);
				// send message
				smtp.send(message);
				// release connection
				smtp.disconnect();
			} catch (MimeException e) {
				errorPrint("ERROR! SMTP MIME failed.");
			} catch (SmtpException e) {
				errorPrint("ERROR! SMTP failed to disconnect or send");
			}
		}
	}

	private void login() throws HttpException, MimeException {
		HttpRequest req = new HttpRequest(loginUrl, "POST");
		Https https = new Https();
		// request headers
		req = addBasicHeaders(req);
		// request parameters
		req.addParameter("USER", username);
		req.addParameter("Password", password);
		req.addParameter("smauthreason", "0");
		req.addParameter("target", scriptTarget);
		req.addParameter("smagentname", scriptSmAgentName);
		// get response
		HttpResponse resp = https.getResponse(req);
		
		// print resp
		https.getResponseToFile(req, new File("response.log"));
		
		// get all cookies from response
		Enumeration<HttpCookie> e = resp.getCookies();
		//HttpCookie session = null;
		while (e.hasMoreElements()) {
			HttpCookie h = e.nextElement();
			if (h.getCookieName().equals("SMSESSION")) {
				smSession = h;
			}
		}
		if (smSession == null) {
			errorPrint("Critical Error! No SMSESSION cookie was received.");
			System.exit(0);
		}
	}
	
	private AspxSession extractStates(String dnr) throws HttpException, MimeException, IOException {
		HttpRequest req = new HttpRequest( (apartmentUrl + dnr) , "GET");
		Https https = new Https();
		// Request headers
		req = addBasicHeaders(req);
		// set session cookies
		req.addCookie(smSession.getCookieName(), smSession.getCookieValue());
		req.addCookie(aspSession.getCookieName(), aspSession.getCookieValue());
		// get response
		HttpResponse resp = https.getResponse(req);
		// get all cookies from response
		Enumeration<HttpCookie> e = resp.getCookies();
		//HttpCookie WT_FPC = null;
		while (e.hasMoreElements()) {
			HttpCookie h = e.nextElement();
			if (h.getCookieName().equals("ASP.NET_SessionId")) {
				aspSession = h;
			}
			if (h.getCookieName().equals("SMSESSION")) {	// update smsession
				smSession = h;
			}
		}
		// process the states found inside the body
		return extractAspxStates(resp.getBody());
	}

	private long register(String dnr) throws HttpException, MimeException, IOException {
		// start timing
		long startTime = System.currentTimeMillis();
		
		AspxSession curr = extractStates(dnr);
		apply(dnr, curr.getViewState(), curr.getEventValidation());
		long finalTime = System.currentTimeMillis() - startTime;
		
		return finalTime;
	}
	
	private void apply(String dnr, HttpParameter viewState, HttpParameter eventValidation) throws HttpException, MimeException {
		String URL = apartmentUrl + dnr;
		HttpRequest req = new HttpRequest(URL, "POST");
		Https https = new Https();
		// request headers
		req = addBasicHeaders(req);
		// request parameters
		req.addParameter("uiButtonMarkeraIntresse", "Anmäl intresse");
		req.addParameter(viewState);
		req.addParameter(eventValidation);
		// set session cookies
		req.addCookie(smSession.getCookieName(), smSession.getCookieValue());
		req.addCookie(aspSession.getCookieName(), aspSession.getCookieValue());
		// get response
		HttpResponse resp = https.getResponse(req);
		// get all cookies from response
		Enumeration<HttpCookie> e = resp.getCookies();
		while (e.hasMoreElements()) {
			HttpCookie h = e.nextElement();
			if (h.getCookieName().equals("ASP.NET_SessionId")) {
				aspSession = h;
			}
			if (h.getCookieName().equals("SMSESSION")) {	// update smsession
				smSession = h;
			}
		}
	}

	public HttpRequest addBasicHeaders(HttpRequest req) {
		try {
			// request headers
			req.addHeader("Content-type", "application/x-www-form-urlencoded");
			req.addHeader("Keep-Alive", "300");
			req.addHeader("Connection", "keep-alive");
			req.addHeader("Cache-Control", "no-cache");
			req.addHeader("Accept-Charset", "ISO-8859-1,utf-8;q=0.7,*;q=0.7");
			// set user agent
			req.setUserAgent("Mozilla/4.0 (compatible; MSIE 5.5; Windows NT)");
		} catch (MimeException e1) {
			errorPrint("ERROR! Could not add basic headers.");
		} catch (HttpException e) {
			errorPrint("ERROR! Could not add basic headers.");
		}
		return req;
	}
	
	private AspxSession extractAspxStates(String content) throws IOException {
		// process the states found inside the body by extracting from content
		HttpParameter viewState = new HttpParameter("__VIEWSTATE", getTagValue("__VIEWSTATE", content));
		HttpParameter eventValidation = new HttpParameter("__EVENTVALIDATION", getTagValue("__EVENTVALIDATION", content));
		
		// throw exception if these return null
		if (viewState.getValue() == null || eventValidation.getValue() == null) {
			throw new IOException();
		}
		// return an AspxSession object
		return new AspxSession(viewState, eventValidation);
	}
	
	private ArrayList<String> extract_dnr(String body) throws IOException {
			 	// make a string reader out of the html body
			 	BufferedReader in = new BufferedReader(new StringReader(body));
		    	// for returning
		    	ArrayList<String> retVal = new ArrayList<String>(); 
				// construct regex
		    	String regex = "Dnr:\\s\\d\\d\\d\\d\\d\\d\\d";
		    	Pattern dnrPattern = Pattern.compile(regex);    	
		    	String textLine;
		    	boolean found = false;
		    	if (verbose == true) {
		    		condPrint("\nFound:\n");
    			}
		    	while ( (textLine = in.readLine()) != null ) {
		    		Matcher matcher = dnrPattern.matcher(textLine);
		    		while (matcher.find()) {
		    			String tmp = matcher.group().substring(5, matcher.group().length());
		    			retVal.add(tmp);
		    			found = true;
		    			
		    			if (verbose == true) {
		    				condPrint( tmp + ", " );
		    			}
		            }
		    	}
		        if(!found){
		            condPrint("None found.");
		        }
		        else if (verbose == false ){
		        	condPrint( "\n* " + new Integer(retVal.size()).toString() + " unregistered apartment(s) found." );
		        }
		    	return retVal;
		    }
	
	private String getSearchPage() throws HttpException, MimeException {
		// construct HttpRequest and Https
		HttpRequest req = new HttpRequest(pollingPage, "GET");
		Https https = new Https();
		// make a basic request
		req = addBasicHeaders(req);
		// set session cookies
		req.addCookie(smSession.getCookieName(), smSession.getCookieValue());
		if (aspSession != null) {
			req.addCookie(aspSession.getCookieName(), aspSession.getCookieValue());
		}
		// get response
		HttpResponse resp = https.getResponse(req);
		// get all cookies from response
		Enumeration<HttpCookie> e = resp.getCookies();
		//HttpCookie WT_FPC = null;
		while (e.hasMoreElements()) {
			HttpCookie h = e.nextElement();
			if (h.getCookieName().equals("ASP.NET_SessionId")) {
				aspSession = h;
			}
			if (h.getCookieName().equals("SMSESSION")) {	// update smsession
				smSession = h;
			}
		}
		if (aspSession == null) {
			errorPrint("Critical Error! No ASP.NET_SessionId cookie was received");
			System.exit(0);
		}
		return resp.getBody();
	}
	
	private String doSearch(HttpParameter viewState, HttpParameter eventValidation) throws HttpException, MimeException {
		// construct http request
		HttpRequest req = new HttpRequest(pollingPage, "post");
		Https https = new Https();
		// make a not-so basic request
		String body = null;
		String encoding = "ISO-8859-1";
		try {
			String strHideUnbookable = "";
			if (hideUnbookable == true) {
				strHideUnbookable = 
					URLEncoder.encode("uiUrvalControl$ctl37", encoding) + "=" + "on" + "&";
			}
				
			body = viewState.getName() + "=" +
			URLEncoder.encode(viewState.getValue(), encoding) + "&" +
			
			eventValidation.getName() + "=" +
			URLEncoder.encode(eventValidation.getValue(),encoding) + "&" +
			
			URLEncoder.encode("uiUrvalControl$uiSnabben", encoding) + "=" +
			"on" + "&" +
			
			strHideUnbookable +
			
			URLEncoder.encode("uiButtonSök", encoding) + "=" +
			URLEncoder.encode("Sök",encoding);
		} catch (UnsupportedEncodingException e1) {
			errorPrint("ERROR! URLEncoding failed.");
		}
		// set body
		req.setBody(body);
		// set session cookies
		req.addCookie(smSession.getCookieName(), smSession.getCookieValue());
		// get response
		HttpResponse resp = https.getResponse(req);
		
		// has this session timed out?
		if (resp.getResponseCode() != 200) { // if response was not 200='success'
			condPrint("Timeout detected. ");
			return null;
		}
		// get all cookies from response
		Enumeration<HttpCookie> e = resp.getCookies();
		while (e.hasMoreElements()) {
			HttpCookie h = e.nextElement();
			if (h.getCookieName().equals("ASP.NET_SessionId")) {
				aspSession = h;
			}
			if (h.getCookieName().equals("SMSESSION")) {	// update smsession
				smSession = h;
			}
		}
		return resp.getBody();
	}
	
	private String run(String retBody) {
		
		// There is no active session, or if the previous session had timeout
		if ( smSession == null) {
			// Login
			try {
				login();
			} catch (Exception e) {
				errorPrint("Web login failed. Retrying.");
			} 
			// Perform a 'GET' operation on the apartment search page
			try {
				retBody = getSearchPage();
			} catch (Exception e) {
				errorPrint("Could not perform search. Retrying.");
			} 
		}
		
		// Extract Viewstate and EventValidation states from the search page body
		AspxSession curr = null;
		try {
			curr = extractAspxStates(retBody);
		} catch (IOException e1) {
			errorPrint("Could not extract session states. Retrying.");
			retBody = null;	// timeout
		}
		
		// Send a 'POST' operation to perform a search request on the apartment searching page
		try {
			retBody = doSearch(curr.getViewState(), curr.getEventValidation());
		} catch (Exception e) {
			errorPrint("Failed to send search request. Retrying.");
			retBody = null;	// timeout
		}
		
		// If the previous operation returns an HTML response code other than 200, session must have timed out
		if (retBody == null) {
			// Reset session-related variables
			smSession = null;		
			aspSession = null;
			curr = null;
			// return from this instance
			return null;
		}
		
		// Else if the session is still active
		// Extract dynamic apartment number from the return body of the previous operation
		ArrayList<String> dnrList = null;
		try {
			dnrList = extract_dnr(retBody);
		} catch (IOException e) {
			errorPrint("Could not extract search data. Retrying.");
		}
		
		// Filter out apartments that have already been registered
		if (dnrList.size() != 0) {
			try {
				dnrList = filter(blacklistFile, dnrList);
			} catch (IOException e) {
				condPrint("Filtering failed. Retrying.");
			}
		}
		
		// Register any available apartments
		ArrayList<String> qToBlacklist = new ArrayList<String>();
		if (dnrList.size() != 0) { 
			for ( String dnr : dnrList ) { // iterate over the list and apply each
				try {
					long timeElapsed = register(dnr);	// register
					qToBlacklist.add(dnr);	// blacklist
					Calendar cal = Calendar.getInstance();	// make a timestamp
					cal.setTime(new Date(System.currentTimeMillis()));	// initialize
					String output = "Registered " + dnr + ". Time was " + cal.getTime() + ", elapsed time was " + timeElapsed + " ms.";
					condPrint("\n" + output);
					/* 9. send email (optional) */
					sendMail(apartmentUrl + dnr, output);
				} catch (Exception e) {
					errorPrint("Registration failed. Terminating");
				}
			}
		}
			
		// Save registered apartment numbers to blacklist
		if (dnrList.size() != 0 && qToBlacklist.size() > 0) {
			for ( String dnr : qToBlacklist ) { // iterate over the list and blacklist (up to 3)
				try {
					appendToBlacklist(dnr);
				} catch (IOException e) {
					errorPrint("Could not write to blacklist. Retrying.");
				}
			}
		}
	
		// Make this thread sleep so that we won't overload the server
		condPrint("\nSleeping(" + frequency + " s) ");
		try {
			for (int i = 0; i < frequency; i++) {
				condPrint(".");
				Thread.sleep(1000);
			}
		} catch (InterruptedException e) {
			errorPrint("ERROR! Sleeping failed.");
		}	
		
		// Return any still useful HTML response body
		return retBody;
	}
	
	public void run() {
		// Display version message
		condPrint("ApBotClient 0.35 rc1 started.\n");
		
		// Construct & initialize string which will store HTML body response
		String retBody = null;
		
		// Do continously
		while(true) {
			// should I be sleeping?
			Calendar now = Calendar.getInstance();
			int o = now.get(now.HOUR_OF_DAY);
			if ( o < 8 || o > 17 ) {
				condPrint("zZz...");
				try {
					Thread.sleep( 1800000 );	// snooze for 30 minutes 
				} catch (InterruptedException e) {
					errorPrint("ERROR! Failed to sleep.");
				}	
			}
				
			else { // if we are to be running normally
				retBody = run(retBody);
			}
		}
		
	}
	
}
