package org.forgerock.openam.miami.utils;

public class SimpleUserWrapper {
	private String userName;
	private String userPassword;

	public SimpleUserWrapper(String userName, String userPassword) {
		this.setUserName(userName);
		this.setUserPassword(userPassword);
	}

	public SimpleUserWrapper() {
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getUserPassword() {
		return userPassword;
	}

	public void setUserPassword(String userPassword) {
		this.userPassword = userPassword;
	}

	@Override
	public String toString() {
		return "SimpleUserWrapper [userName=" + userName + ", userPassword=" + userPassword + "]";
	}

}