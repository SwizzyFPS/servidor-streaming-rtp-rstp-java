package com.dolphinss.protocolo.screen;

import java.awt.Dimension;
import java.io.IOException;

import javax.media.CannotRealizeException;
import javax.media.Codec;
import javax.media.Control;
import javax.media.ControllerClosedEvent;
import javax.media.ControllerEvent;
import javax.media.ControllerListener;
import javax.media.DataSink;
import javax.media.Format;
import javax.media.Manager;
import javax.media.MediaException;
import javax.media.MediaLocator;
import javax.media.NoProcessorException;
import javax.media.Owned;
import javax.media.Player;
import javax.media.Processor;
import javax.media.ProcessorModel;
import javax.media.control.QualityControl;
import javax.media.control.TrackControl;
import javax.media.format.VideoFormat;
import javax.media.protocol.ContentDescriptor;
import javax.media.protocol.FileTypeDescriptor;

public class VideoTransmitPantalla {
	// Input MediaLocator
	// Can be a file or http or capture source
	private MediaLocator locator;
	private String ipAddress;
	private String port;

	private Processor processor = null;
	private DataSink rtptransmitter = null;
	private javax.media.protocol.DataSource dataOutput = null;
	

	public VideoTransmitPantalla(MediaLocator locator, String ipAddress, String port) {
		this.locator = locator;
		this.ipAddress = ipAddress;
		this.port = port;
	}

	/**
	 * Starts the transmission. Returns null if transmission started ok.
	 * Otherwise it returns a string with the reason why the setup failed.
	 */
	public synchronized String start() {
		String result;

		// Create a processor for the specified media locator
		// and program it to output JPEG/RTP
		result = createProcessor();
		if (result != null)
			return result;

		// Create an RTP session to transmit the output of the
		// processor to the specified IP address and port no.
		result = createTransmitter();
		if (result != null) {
			processor.close();
			processor = null;
			return result;
		}

		// Start the transmission
		processor.start();

		return null;
	}

	/**
	 * Stops the transmission if already started
	 */
	public void stop() {
		synchronized (this) {
			if (processor != null) {
				processor.stop();
				processor.close();
				processor = null;
				rtptransmitter.close();
				rtptransmitter = null;
			}
		}
	}

	private String createProcessor() {
		if (locator == null)
			return "Locator is null";

		DataSourcePantalla ds;
		javax.media.protocol.DataSource clone;

		try {
			ds = new DataSourcePantalla();
			ds.setLocator(locator);
			clone = javax.media.Manager.createCloneableDataSource(ds);
		} catch (Exception e) {
			System.out.println(e.getMessage());
			return "Couldn't create DataSource";
		}

		try {
			ds.connect();
			clone.connect();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Format[] outputFormat=new Format[1];
		VideoFormat v=new VideoFormat(VideoFormat.JPEG_RTP);
		outputFormat[0]=v;
		FileTypeDescriptor outputType = new FileTypeDescriptor(
				FileTypeDescriptor.RAW_RTP);
		ProcessorModel processorModel = new ProcessorModel(clone,
				outputFormat, outputType);

		// Try to create a processor to handle the input media locator
		try {
//			processor = Manager.createProcessor(clone);
			processor = Manager.createRealizedProcessor(processorModel);
		} catch (NoProcessorException npe) {
			return "Couldn't create processor";
		} catch (IOException ioe) {
			return "IOException creating processor";
		} catch (CannotRealizeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		boolean result = waitForState(processor, Processor.Configured);
		if (result == false)
			return "Couldn't configure processor";
		
		TrackControl[] tracks = processor.getTrackControls();
		// Search through the tracks for a video track
		for (int i = 0; i < tracks.length; i++) {
			Format format = tracks[i].getFormat();
			if (tracks[i].isEnabled() && format instanceof VideoFormat) {

				// Found a video track. Try to program it to output JPEG/RTP
				// Make sure the sizes are multiple of 8's.
				float frameRate = 30;//((VideoFormat) format).getFrameRate();
				Dimension size = new Dimension(1280, 800);//((VideoFormat) format).getSize();
				int w = (size.width % 8 == 0 ? size.width
						: (int) (size.width / 8) * 8);
				int h = (size.height % 8 == 0 ? size.height
						: (int) (size.height / 8) * 8);
				VideoFormat jpegFormat = new VideoFormat(VideoFormat.JPEG_RTP,
						new Dimension(w, h), Format.NOT_SPECIFIED,
						Format.byteArray, frameRate);
				tracks[i].setFormat(jpegFormat);
			} else
				tracks[i].setEnabled(false);
		}
//		// Set the output content descriptor to RAW_RTP
		ContentDescriptor cd = new ContentDescriptor(ContentDescriptor.RAW_RTP);
		processor.setContentDescriptor(cd);
		
		// Get the output data source of the processor
		dataOutput = processor.getDataOutput();
		return null;
	}

	// Creates an RTP transmit data sink. This is the easiest way to create
	// an RTP transmitter. The other way is to use the RTPSessionManager API.
	// Using an RTP session manager gives you more control if you wish to
	// fine tune your transmission and set other parameters.
	private String createTransmitter() {
		// Create a media locator for the RTP data sink.
		// For example:
		// rtp://129.130.131.132:42050/video
		String rtpURL = "rtp://" + ipAddress + ":" + port + "/video";
		MediaLocator outputLocator = new MediaLocator(rtpURL);

		// Create a data sink, open it and start transmission. It will wait
		// for the processor to start sending data. So we need to start the
		// output data source of the processor. We also need to start the
		// processor itself, which is done after this method returns.
		try {
			rtptransmitter = Manager.createDataSink(dataOutput, outputLocator);
			rtptransmitter.open();
			rtptransmitter.start();
			dataOutput.start();
		} catch (MediaException me) {
			return "Couldn't create RTP data sink";
		} catch (IOException ioe) {
			return "Couldn't create RTP data sink";
		}

		return null;
	}

	/**
	 * Setting the encoding quality to the specified value on the JPEG encoder.
	 * 0.5 is a good default.
	 */
	void setJPEGQuality(Player p, float val) {

		Control cs[] = p.getControls();
		QualityControl qc = null;
		VideoFormat jpegFmt = new VideoFormat(VideoFormat.JPEG);

		// Loop through the controls to find the Quality control for
		// the JPEG encoder.
		for (int i = 0; i < cs.length; i++) {

			if (cs[i] instanceof QualityControl && cs[i] instanceof Owned) {
				Object owner = ((Owned) cs[i]).getOwner();

				// Check to see if the owner is a Codec.
				// Then check for the output format.
				if (owner instanceof Codec) {
					Format fmts[] = ((Codec) owner)
							.getSupportedOutputFormats(null);
					for (int j = 0; j < fmts.length; j++) {
						if (fmts[j].matches(jpegFmt)) {
							qc = (QualityControl) cs[i];
							qc.setQuality(val);
							System.err.println("- Setting quality to " + val
									+ " on " + qc);
							break;
						}
					}
				}
				if (qc != null)
					break;
			}
		}
	}

	/****************************************************************
	 * Convenience methods to handle processor's state changes.
	 ****************************************************************/

	private Integer stateLock = new Integer(0);
	private boolean failed = false;

	Integer getStateLock() {
		return stateLock;
	}

	void setFailed() {
		failed = true;
	}

	private synchronized boolean waitForState(Processor p, int state) {
		p.addControllerListener(new StateListener());
		failed = false;

		// Call the required method on the processor
		if (state == Processor.Configured) {
			p.configure();
		} else if (state == Processor.Realized) {
			p.realize();
		}

		// Wait until we get an event that confirms the
		// success of the method, or a failure event.
		// See StateListener inner class
		while (p.getState() < state && !failed) {
			synchronized (getStateLock()) {
				try {
					getStateLock().wait();
				} catch (InterruptedException ie) {
					return false;
				}
			}
		}

		if (failed)
			return false;
		else
			return true;
	}

	/****************************************************************
	 * Inner Classes
	 ****************************************************************/

	class StateListener implements ControllerListener {

		public void controllerUpdate(ControllerEvent ce) {

			// If there was an error during configure or
			// realize, the processor will be closed
			if (ce instanceof ControllerClosedEvent)
				setFailed();

			// All controller events, send a notification
			// to the waiting thread in waitForState method.
			if (ce instanceof ControllerEvent) {
				synchronized (getStateLock()) {
					getStateLock().notifyAll();
				}
			}
		}
	}
}
