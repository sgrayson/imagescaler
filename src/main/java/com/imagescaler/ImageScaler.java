package com.imagescaler;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.ArrayList;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ImageScaler {
	
	public static void scaleImageFile(File inputFile, File outputFile, int width, int height) throws Exception {
		String extension = inputFile.getName().split("\\.")[1];
		
		if (extension.equals("gif")) {
			FileInputStream fistream = new FileInputStream(inputFile);
			
			List<ImageFrame> frames = readGif(fistream);
			for (ImageFrame frame : frames) {
				BufferedImage image = getScaledImage(frame.getImage(), width, height);
				frame.setWidth(width);
				frame.setHeight(height);
				frame.setImage(image);
			}
			
			ImageOutputStream output = new FileImageOutputStream(outputFile);
			GifSequenceWriter writer = new GifSequenceWriter(output, frames.get(0).getImage().getType(), frames.get(0).getDelay(), true);
			
			for (ImageFrame frame : frames) {
				BufferedImage image = frame.getImage();
				writer.writeToSequence(image);
			}
			
			writer.close();
			output.close();
		} else {
			BufferedImage originalImage = ImageIO.read(inputFile);
			BufferedImage resizedImage = getScaledImage(originalImage, width, height);
			ImageIO.write(resizedImage, "png", outputFile);
		}
	}
	
	public static List<ImageFrame> readGif(InputStream stream) throws IOException {
	    List<ImageFrame> frames = new ArrayList<ImageFrame>(2);

	    ImageReader reader = (ImageReader) ImageIO.getImageReadersByFormatName("gif").next();
	    reader.setInput(ImageIO.createImageInputStream(stream));

	    int lastx = 0;
	    int lasty = 0;

	    int width = -1;
	    int height = -1;

	    IIOMetadata metadata = reader.getStreamMetadata();

	    Color backgroundColor = null;

	    if (metadata != null) {
	        IIOMetadataNode globalRoot = (IIOMetadataNode) metadata.getAsTree(metadata.getNativeMetadataFormatName());

	        NodeList globalColorTable = globalRoot.getElementsByTagName("GlobalColorTable");
	        NodeList globalScreenDescriptor = globalRoot.getElementsByTagName("LogicalScreenDescriptor");

	        if (globalScreenDescriptor != null && globalScreenDescriptor.getLength() > 0) {
	            IIOMetadataNode screenDescriptor = (IIOMetadataNode) globalScreenDescriptor.item(0);

	            if (screenDescriptor != null) {
	                width = Integer.parseInt(screenDescriptor.getAttribute("logicalScreenWidth"));
	                height = Integer.parseInt(screenDescriptor.getAttribute("logicalScreenHeight"));
	            }
	        }

	        if (globalColorTable != null && globalColorTable.getLength() > 0) {
	            IIOMetadataNode colorTable = (IIOMetadataNode) globalColorTable.item(0);

	            if (colorTable != null) {
	                String bgIndex = colorTable.getAttribute("backgroundColorIndex");

	                IIOMetadataNode colorEntry = (IIOMetadataNode) colorTable.getFirstChild();
	                while (colorEntry != null) {
	                    if (colorEntry.getAttribute("index").equals(bgIndex)) {
	                        int red = Integer.parseInt(colorEntry.getAttribute("red"));
	                        int green = Integer.parseInt(colorEntry.getAttribute("green"));
	                        int blue = Integer.parseInt(colorEntry.getAttribute("blue"));

	                        backgroundColor = new Color(red, green, blue);
	                        break;
	                    }

	                    colorEntry = (IIOMetadataNode) colorEntry.getNextSibling();
	                }
	            }
	        }
	    }

	    BufferedImage master = null;
	    boolean hasBackround = false;

	    for (int frameIndex = 0;; frameIndex++) {
	        BufferedImage image;
	        try {
	            image = reader.read(frameIndex);
	        }
	        catch (IndexOutOfBoundsException io) {
	            break;
	        }

	        if (width == -1 || height == -1) {
	            width = image.getWidth();
	            height = image.getHeight();
	        }

	        IIOMetadataNode root = (IIOMetadataNode) reader.getImageMetadata(frameIndex).getAsTree("javax_imageio_gif_image_1.0");
	        IIOMetadataNode gce = (IIOMetadataNode) root.getElementsByTagName("GraphicControlExtension").item(0);
	        NodeList children = root.getChildNodes();

	        int delay = Integer.valueOf(gce.getAttribute("delayTime"));

	        String disposal = gce.getAttribute("disposalMethod");

	        if (master == null) {
	            master = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
	            master.createGraphics().setColor(backgroundColor);
	            master.createGraphics().fillRect(0, 0, master.getWidth(), master.getHeight());

	            hasBackround = image.getWidth() == width && image.getHeight() == height;

	            master.createGraphics().drawImage(image, 0, 0, null);
	        }
	        else {
	            int x = 0;
	            int y = 0;

	            for (int nodeIndex = 0; nodeIndex < children.getLength(); nodeIndex++) {
	                Node nodeItem = children.item(nodeIndex);

	                if (nodeItem.getNodeName().equals("ImageDescriptor")) {
	                    NamedNodeMap map = nodeItem.getAttributes();

	                    x = Integer.valueOf(map.getNamedItem("imageLeftPosition").getNodeValue());
	                    y = Integer.valueOf(map.getNamedItem("imageTopPosition").getNodeValue());
	                }
	            }

	            if (disposal.equals("restoreToPrevious")) {
	                BufferedImage from = null;
	                for (int i = frameIndex - 1; i >= 0; i--) {
	                    if (!frames.get(i).getDisposal().equals("restoreToPrevious") || frameIndex == 0) {
	                        from = frames.get(i).getImage();
	                        break;
	                    }
	                }

	                ColorModel model = from.getColorModel();
	                boolean alpha = from.isAlphaPremultiplied();
	                WritableRaster raster = from.copyData(null);
	                master = new BufferedImage(model, raster, alpha, null);
	            } else if (disposal.equals("restoreToBackgroundColor") && backgroundColor != null) {
	                if (!hasBackround || frameIndex > 1) {
	                    master.createGraphics().fillRect(lastx, lasty, 
	                    		frames.get(frameIndex - 1).getWidth(), 
	                    		frames.get(frameIndex - 1).getHeight());
	                }
	            }
	            master.createGraphics().drawImage(image, x, y, null);

	            lastx = x;
	            lasty = y;
	        }

	        BufferedImage copy;

	        ColorModel model = master.getColorModel();
	        boolean alpha = master.isAlphaPremultiplied();
	        WritableRaster raster = master.copyData(null);
	        copy = new BufferedImage(model, raster, alpha, null);
	                
	        frames.add(new ImageFrame(copy, delay, disposal, image.getWidth(), image.getHeight()));

	        master.flush();
	    }
	    reader.dispose();

	    return frames;
	}
	
	private static BufferedImage getScaledImage(BufferedImage originalImage, int width, int height) {
		int type = originalImage.getType() == 0? BufferedImage.TYPE_INT_ARGB : originalImage.getType();
		BufferedImage resizedImage = new BufferedImage(width, height, type);
		
		Graphics2D g = resizedImage.createGraphics();
		g.setComposite(AlphaComposite.Src);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
		g.drawImage(originalImage, 0, 0, width, height, null);
		g.dispose();
		
		return resizedImage;
	}
	
	public static void main(String[] args) throws Exception {	
		scaleImageFile(new File("/Users/seangrayson/Development/angrybirds_retina.gif"),
				new File("/Users/seangrayson/Development/angrybirds.gif"), 320, 50);
		
		scaleImageFile(new File("/Users/seangrayson/Development/groupon_retina.png"),
				new File("/Users/seangrayson/Development/groupon.png"), 320, 50);
		
		scaleImageFile(new File("/Users/seangrayson/Development/hotels_retina.jpg"),
				new File("/Users/seangrayson/Development/hotels.jpg"), 320, 50);
	}
}

