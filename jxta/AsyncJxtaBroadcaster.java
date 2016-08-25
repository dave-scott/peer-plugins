package com.haplos.xub.plugins.jxta;

import com.haplos.xub.Core.IAtomizable;
import com.haplos.xub.Core.IAtomizer;
import com.haplos.xub.Core.StreamAtomizer;
import com.haplos.xub.Core.CacheAtomizable;
import com.haplos.xub.Connectable.IInitable;
import com.haplos.xub.Connectable.IReceiver;
import com.haplos.xub.Connectable.XubException;
import com.haplos.xub.Filters.ISink;
import com.haplos.xub.Filters.ISinkFilter;
import com.haplos.xub.Filters.Null.NullFilterCreator;
import com.haplos.xub.Toolkit.ComponentFactory;

import net.jxta.peergroup.PeerGroup;
import net.jxta.protocol.PipeAdvertisement;
import net.jxta.discovery.DiscoveryService;
import net.jxta.endpoint.Message;
import net.jxta.endpoint.StringMessageElement;
import net.jxta.endpoint.ByteArrayMessageElement;
import net.jxta.pipe.PipeService;
import net.jxta.pipe.OutputPipe;
import net.jxta.pipe.OutputPipeListener;
import net.jxta.pipe.OutputPipeEvent;

import java.util.Map;
import java.util.Enumeration;
import java.io.OutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;

/*
 * by Dave Scott, 2005
 *
 * Copyright and license conditions are contained in the file named
 * 'LICENSE.TXT' which is distributed with this source code.  Online
 * copies of the license conditions can be found at
 *         http://haplos.com/licences/license.html
 */

/**
 * Class which listens on a port for requests for a socket connection; new connections are given a
 * new thread which forwards incoming messages to the SyncAsyncBridge.
 * 
 * @author Dave Scott
 */

public class AsyncJxtaBroadcaster implements IInitable, ISink, IReceiver
{
	private PeerGroup		m_group = null;
	private String			m_serviceName = null;
	private ISinkFilter		m_sinkFilter = NullFilterCreator.getCreator();
	private String			m_returnPipe = null;

	public AsyncJxtaBroadcaster()
	{
	}
	
	public boolean init(Map parameters)
	{
		m_group = JxtaUtilities.getGroup(parameters);
		m_serviceName = ComponentFactory.getParameter("service-id", null, parameters);
		m_returnPipe = ComponentFactory.getParameter("response-handler", null, parameters);
		
		return true;
	}
	
	public boolean setSinkFilter(ISinkFilter filter)
	{
		m_sinkFilter = filter;
		if ( m_sinkFilter == null )
			m_sinkFilter = NullFilterCreator.getCreator();
		return true;
	}
	
	public boolean receive(IAtomizable message) throws XubException
	{
		Enumeration advertisements = JxtaUtilities.discoverAdvertisements(m_group, DiscoveryService.ADV, m_serviceName, 5);
		if ( advertisements == null ) return true;
		PipeAdvertisement pipeAdv = null;
		PipeService pipeService = m_group.getPipeService();
		IAtomizer output = null;
		if ( message.isReadOnce() )
			message = CacheAtomizable.copy(message);
		
		while ( advertisements.hasMoreElements() )
		{
			pipeAdv = (PipeAdvertisement)advertisements.nextElement();
			try
			{
				pipeService.createOutputPipe(pipeAdv, new MyListener(message, m_sinkFilter, m_returnPipe));
			}
			catch (IOException ioe)
			{
			}
		}
		
		return true;
	}
	
	public static  class MyListener implements OutputPipeListener
	{
		IAtomizable 		m_message = null;
		private ISinkFilter	m_sinkFilter = null;
		private String		m_returnPipe = null;
		
		public MyListener(IAtomizable message, ISinkFilter sinkFilter, String returnPipe)
		{
			m_message = message;
			m_sinkFilter = sinkFilter;
			m_returnPipe = returnPipe;
		}
		
		public void outputPipeEvent(OutputPipeEvent event)
		{
			OutputPipe pipe = event.getOutputPipe();
			Message message = new Message();
			if ( m_returnPipe != null )
				message.addMessageElement(new StringMessageElement("response-handler", m_returnPipe, null));
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			m_message.write(null, m_sinkFilter.getInstance(bytes));
			message.addMessageElement(new ByteArrayMessageElement ("message", null, bytes.toByteArray(), null));

			try
			{
				pipe.send(message);
			}
			catch (IOException ioe)
			{
				ioe.printStackTrace();
			}
			pipe.close();
		}
	}
}

