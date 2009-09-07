import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class ConfigReader {
	private BufferedReader txt;
	
	public ConfigReader(String fileName, int buffersize) throws IOException {
		// get stream
		txt = new BufferedReader(new FileReader(fileName));
		txt.mark(buffersize);
	}

	/**
	 * @return the value of the read key inside the config file.
	 * @throws IOException 
	 */
	public String readKey(String key) throws IOException {
		txt.reset();
		
		String str;
		while ( (str = txt.readLine()) != null) {	// while not EOF
			if (str.length() == 0) { // if this is an empty line
				// do nothing
			}
			else if (str.length() > 0 ) { // either a useful line or a commented line
				if (str.substring(0,1).equals("#")) {	// if this is a commented line
					// do nothing
				}
				else { // if this is a useful line
					// check if this is the key
					String tmp = null;
					for (int i = 0; i < key.length() && str.charAt(i) == key.charAt(i); i++) {
						tmp = str.substring(0, i+1);
						if (str.substring(i+1,i+2).equals(" ") && tmp.equals(key)) { // bingo, we found our key, the value we seek is nearby
							str = str.substring(i+1, str.length());
							str = removeSpacesAndEquals(str);
							
							i = 0;
							while ( i != str.length()  ) {
								if (str.charAt(i) == 32) { // 32 = space
									break;
								}
								else {
									i++;
								}
								
							}
							
							str = str.substring(0, i);
							return str; // return the value found
						}
					}
				}
			}
		}
		return null; // not found
	}
		
	private String removeSpacesAndEquals(String input) {
		for (int i = 0; i < input.length(); i++) {
			if ( input.substring(i, i+1).equals("=") || input.substring(i, i+1).equals(" ") ) {
				input = input.substring(i+1, input.length());
			}
			else {
				return input;
			}
		}
		return input;	// whole line was removed
	}
	
}
