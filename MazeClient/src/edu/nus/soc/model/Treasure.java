package edu.nus.soc.model;

import java.io.Serializable;

public class Treasure implements Serializable{
	private Position position;
	private boolean flag;
	
	public Treasure(Position position) {
		this.setPosition(position);
		this.setFlag(true);
	}

	public boolean isFlag() {
		return flag;
	}

	public void setFlag(boolean flag) {
		this.flag = flag;
	}

	public Position getPosition() {
		return position;
	}

	public void setPosition(Position position) {
		this.position = position;
	}
}
