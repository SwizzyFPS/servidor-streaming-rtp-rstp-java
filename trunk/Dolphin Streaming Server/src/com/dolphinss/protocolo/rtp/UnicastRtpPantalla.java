package com.dolphinss.protocolo.rtp;

import java.awt.Dimension;
import java.io.IOException;
import java.net.InetAddress;

import javax.media.CannotRealizeException;
import javax.media.ConfigureCompleteEvent;
import javax.media.ControllerEvent;
import javax.media.ControllerListener;
import javax.media.EndOfMediaEvent;
import javax.media.Format;
import javax.media.Manager;
import javax.media.MediaLocator;
import javax.media.NoProcessorException;
import javax.media.NotRealizedError;
import javax.media.Processor;
import javax.media.ProcessorModel;
import javax.media.RealizeCompleteEvent;
import javax.media.control.TrackControl;
import javax.media.format.VideoFormat;
import javax.media.protocol.ContentDescriptor;
import javax.media.protocol.DataSource;
import javax.media.protocol.FileTypeDescriptor;
import javax.media.rtp.SendStreamListener;

import com.dolphinss.HiloCliente;
import com.dolphinss.protocolo.screen.DataSourcePantalla;

public class UnicastRtpPantalla extends UnicastRtp implements ControllerListener,
SendStreamListener {
	
	protected int width=1280;
	protected int height=800;
	protected int frameRate=25;

	public UnicastRtpPantalla(String file, InetAddress d_IP, int l_rtp,
			int d_rtp, int track) {
		super(file, d_IP, l_rtp, d_rtp, track);
		// TODO Auto-generated constructor stub
	}
	
	public boolean createMyProcessor() {
		if(HiloCliente.DEBUG){
			System.out.println("El medialocator del screen es: "+url);
		}
		MediaLocator ml=new MediaLocator(url);
		
		DataSource clone=null;

		try {
			ds = new DataSourcePantalla();
			ds.setLocator(ml);
			clone = javax.media.Manager.createCloneableDataSource(ds);
		} catch (Exception e) {
			System.out.println("Excepcion al crear el DataSourcePantalla; "+e.getMessage());
		}

		try {
			ds.connect();
			clone.connect();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		Format[] outputFormat=new Format[1];
		VideoFormat vv=new VideoFormat(VideoFormat.JPEG_RTP);
		outputFormat[0]=vv;
		FileTypeDescriptor outputType = new FileTypeDescriptor(
				FileTypeDescriptor.RAW_RTP);
		ProcessorModel processorModel = new ProcessorModel(clone,
				outputFormat, outputType);
		
		// Try to create a processor to handle the input media locator
		try {
			processor = Manager.createProcessor(ds);
			processor.addControllerListener(this);
			processor.configure();
//			processor.prefetch();
		} catch (NoProcessorException npe) {
			System.out.println("Salta excepcion: NoProcessorException; Con mensaje: "+npe.getMessage());
		} catch (IOException ioe) {
			System.out.println("Salta excepcion: IOException; con mensaje: "+ioe.getMessage());
		} 
//		catch (CannotRealizeException e) {
//			// TODO Auto-generated catch block
//			System.out.println("Salta excepcion: CannotRealizeException; con mensaje: "+e.getMessage());
//		} 
		
		while(processor.getState()<Processor.Configured){
			System.out.println("Esperando a configurar el processor...");
		}
				
		return true;
	}

	@Override
	public void controllerUpdate(ControllerEvent evento) {
		// TODO Auto-generated method stub
		if(evento instanceof ConfigureCompleteEvent){
			System.out.println("Entro por evento ConfigureCompleteEvent");
			
			ContentDescriptor cd = new ContentDescriptor(ContentDescriptor.RAW_RTP);
			processor.setContentDescriptor(cd);
			
			configuraPistas();
			
			processor.realize();
		}
		if (evento instanceof RealizeCompleteEvent) {
			System.out.println("Entro por evento RealizeCompleteEvent");
			try {
				ds = processor.getDataOutput();
				createMyRTPManager();
			} catch (NotRealizedError ex) {
				System.out.println("Salta excepcion: NotRealizedError; con mensaje: "+ex.getMessage());
			}
		}

		if (evento instanceof EndOfMediaEvent) {
			System.out.println("Entro por evento EndOfMediaEvent");
			closeMyStream();
			endofMedia = true;
		}
	}
	
	protected void configuraPistas(){
		TrackControl[] tracks = processor.getTrackControls();
		
		// Search through the tracks for a video track
		for (int i = 0; i < tracks.length; i++) {
			Format format = tracks[i].getFormat();
			if (tracks[i].isEnabled() && format instanceof VideoFormat) {

				VideoFormat v = (VideoFormat) tracks[i].getFormat();
				System.out.println("\tFormato video pantalla (primero): "+v);
				
				// Found a video track. Try to program it to output JPEG/RTP
				// Make sure the sizes are multiple of 8's.
				Dimension size = new Dimension(width, height);//((VideoFormat) format).getSize();
				int w = (size.width % 8 == 0 ? size.width
						: (int) (size.width / 8) * 8);
				int h = (size.height % 8 == 0 ? size.height
						: (int) (size.height / 8) * 8);
				VideoFormat jpegFormat = new VideoFormat(VideoFormat.JPEG_RTP,
						new Dimension(w, h), Format.NOT_SPECIFIED,
						Format.byteArray, frameRate);
				tracks[i].setFormat(jpegFormat);
				v = (VideoFormat) tracks[i].getFormat();
				System.out.println("\tFormato video pantalla (despues): "+v);
			} else
				tracks[i].setEnabled(false);
		}
	}
}
