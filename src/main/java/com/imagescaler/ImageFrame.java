package com.imagescaler;

import java.awt.image.BufferedImage;

public class ImageFrame {
	
	private final String disposal;
	private final int delay;
    private BufferedImage image;
    private int width, height;

    public ImageFrame (BufferedImage image, int delay, String disposal, int width, int height) {
        this.image = image;
        this.delay = delay;
        this.disposal = disposal;
        this.width = width;
        this.height = height;
    }

    public BufferedImage getImage() {
        return image;
    }
    
    public void setImage(BufferedImage image) {
    	this.image = image;
    }

    public int getDelay() {
        return delay;
    }
    
    public String getDisposal() {
    	return disposal;
    }

    public int getWidth() {
        return width;
    }
    
    public void setWidth(int width) {
    	this.width = width;
    }

    public int getHeight() {
    	return height;
    }
    
    public void setHeight(int height) {
    	this.height = height;
    }
}