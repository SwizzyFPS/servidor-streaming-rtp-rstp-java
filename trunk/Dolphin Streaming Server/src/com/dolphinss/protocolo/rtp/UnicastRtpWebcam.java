package com.dolphinss.protocolo.rtp;

import java.awt.Dimension;
import java.io.IOException;
import java.net.InetAddress;

import javax.media.CannotRealizeException;
import javax.media.Format;
import javax.media.Manager;
import javax.media.MediaLocator;
import javax.media.NoProcessorException;
import javax.media.Processor;
import javax.media.ProcessorModel;
import javax.media.control.TrackControl;
import javax.media.format.VideoFormat;
import javax.media.protocol.ContentDescriptor;
import javax.media.protocol.DataSource;
import javax.media.protocol.FileTypeDescriptor;

import com.dolphinss.HiloCliente;
import com.dolphinss.protocolo.screen.DataSourcePantalla;

public class UnicastRtpWebcam extends UnicastRtp {

	public UnicastRtpWebcam(String file, InetAddress d_IP, int l_rtp,
			int d_rtp, int track) {
		super(file, d_IP, l_rtp, d_rtp, track);
		// TODO Auto-generated constructor stub
	}
	
	/**
	 * @Override
	 */
	public boolean createMyProcessor() {
		if(HiloCliente.DEBUG){
			System.out.println("URL de createMyProcessor: "+url);
		}
		MediaLocator locator=new MediaLocator(url);
		
		DataSourcePantalla ds=null;
		DataSource clone=null;
		
		try {
			ds = new DataSourcePantalla();
			ds.setLocator(locator);
			clone = javax.media.Manager.createCloneableDataSource(ds);
			
			ds.connect();
			clone.connect();
		} catch (Exception ex) {
			System.out.println(ex.getMessage());
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
			processor = Manager.createRealizedProcessor(processorModel);
			processor.addControllerListener(this);
		} catch (NoProcessorException npe) {
			System.out.println(npe.getMessage());
		} catch (IOException ioe) {
			System.out.println(ioe.getMessage());
		} catch (CannotRealizeException ex) {
			// TODO Auto-generated catch block
			System.out.println(ex.getMessage());
		}
		
		boolean result = waitForState(processor, Processor.Configured);
		if (result == false){
			System.err.println("No se pudo crear el processor");
			return false;
		}
		
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
//		dataOutput = processor.getDataOutput();
		return true;
	}
	
}
