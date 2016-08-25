package com.haplos.xub.plugins.jxta;

import com.haplos.xub.Core.IAtomizable;
import com.haplos.xub.Connectable.IInitable;
import com.haplos.xub.Connectable.ICloseable;
import com.haplos.xub.Connectable.ISender;
import com.haplos.xub.Connectable.ISendHandler;
import com.haplos.xub.Connectable.XubException;
import com.haplos.xub.Filters.IPump;
import com.haplos.xub.Filters.IPumpFilter;
import com.haplos.xub.Filters.Null.NullFilterCreator;
import com.haplos.xub.Toolkit.ComponentFactory;

import net.jxta.peergroup.PeerGroup;
import net.jxta.protocol.PipeAdvertisement;
import net.jxta.endpoint.Message;
import net.jxta.endpoint.MessageElement;
import net.jxta.pipe.PipeService;
import net.jxta.pipe.InputPipe;
import net.jxta.pipe.PipeMsgListener;
import net.jxta.pipe.PipeMsgEvent;

import java.io.ByteArrayInputStream;
import java.util.Map;

/*
 * by Dave Scott, 2005
 *
 * Copyright and license conditions are contained in the file named
 * 'LICENSE.TXT' which is distributed with this source code.  Online
 * copies of the license conditions can be found at
 *         http://haplos.com/licences/license.html
 */

/**
 * Class which listens for connections on a port, spawns a thread to deal with messages on a socket's input stream.
 * 
 * @author Dave Scott
 */

public class AsyncJxtaSender implements ISender, IInitable, IPump, ICloseable, PipeMsgListener
{
	private Thread 			m_serverThread = null;
	private PeerGroup		m_group = null;
	private PipeAdvertisement	m_advertisement = null;
	private IPumpFilter		m_filter = NullFilterCreator.getCreator();
	private ISendHandler		m_handler = null;
	private InputPipe		m_pipe = null;

	public AsyncJxtaSender()
	{
	}
	
	public boolean init(Map parameters)
	{
		m_group = JxtaUtilities.getGroup(parameters);
		if ( m_group == null ) return false;
		m_advertisement = JxtaUtilities.getPipeAdvertisement(parameters);
		if ( m_advertisement == null ) return false;
		return true;
	}
	
	public synchronized boolean close()
	{
		if ( m_pipe != null )
		{
			m_pipe.close();
			m_pipe = null;
		}
		return true;
	}
	
	public boolean setPumpFilter(IPumpFilter filter)
	{
		m_filter = filter;
		if ( m_filter == null ) m_filter = NullFilterCreator.getCreator();
		return true;
	}
	
	public boolean connect(ISendHandler handler)
	{
		m_handler = handler;
		if ( m_handler == null ) return true;
		System.out.println ("Opening AsyncJxtaSender.");
		PipeService pipeService = m_group.getPipeService();
		try
		{
			m_pipe = pipeService.createInputPipe(m_advertisement, this);
		}
		catch (Exception e)
		{
			return false;
		}
		return true;
	}

	public void pipeMsgEvent(PipeMsgEvent event)
	{
		Message message = event.getMessage();
		MessageElement element = message.getMessageElement("message");
		if ( element == null ) return;	//  Must at last have empty 'message' element
		try
		{
			m_handler.send(m_filter.getInstance(new ByteArrayInputStream(element.getBytes(false))));
		}
		catch (Exception e)
		{
			// Do nothing
		}
	}
}

