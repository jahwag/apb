import com.jscape.inet.http.HttpParameter;

public class AspxSession {
	private HttpParameter viewState;
	private HttpParameter eventValidation;
	
	public AspxSession(HttpParameter viewState, HttpParameter eventValidation) {
		this.setViewState(viewState);
		this.setEventValidation(eventValidation);
	}

	public void setViewState(HttpParameter viewState) {
		this.viewState = viewState;
	}

	public HttpParameter getViewState() {
		return viewState;
	}

	public void setEventValidation(HttpParameter eventValidation) {
		this.eventValidation = eventValidation;
	}

	public HttpParameter getEventValidation() {
		return eventValidation;
	}
}
