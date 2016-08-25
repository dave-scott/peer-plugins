package com.haplos.xub.plugins.jxta;

import com.haplos.xub.Core.IAtomizable;
import com.haplos.xub.Core.IAtomizer;
import com.haplos.xub.Connectable.IInitable;
import com.haplos.xub.Connectable.IRequestHandler;
import com.haplos.xub.Connectable.IReceiver;
import com.haplos.xub.Connectable.XubException;
import com.haplos.xub.Filters.IPump;
import com.haplos.xub.Filters.ISink;
import com.haplos.xub.Filters.IPumpFilter;
import com.haplos.xub.Filters.ISinkFilter;
import com.haplos.xub.Filters.Null.NullFilterCreator;
import com.haplos.xub.Toolkit.ComponentFactory;

import java.io.InputStream;
import net.jxta.peergroup.PeerGroup;
import net.jxta.protocol.PipeAdvertisement;
import net.jxta.socket.JxtaSocket;

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
 * Class which listens on a port for requests for a socket connection.
 * 
 * @author Dave Scott
 */

public class JxtaRequestHandler implements IInitable, IPump, ISink, IRequestHandler
{
	private int			m_timeout = 30;
	private PeerGroup		m_group = null;
	private PipeAdvertisement	m_advertisement = null;

	private IPumpFilter		m_pumpFilter = NullFilterCreator.getCreator();
	private ISinkFilter		m_sinkFilter = NullFilterCreator.getCreator();

	public JxtaRequestHandler()
	{
	}
	
	public boolean init(Map parameters)
	{
System.out.println("JxtaRequestHandler: init.");
		m_group = JxtaUtilities.getGroup(parameters);
		m_advertisement = JxtaUtilities.findPipeAdvertisement(parameters);
		m_timeout = ComponentFactory.getIntParameter(ComponentFactory.TIMEOUT, m_timeout, parameters);
		return true;
	}
	
	public boolean setPumpFilter(IPumpFilter filter)
	{
		m_pumpFilter = filter;
		if ( m_pumpFilter == null )
			m_pumpFilter = NullFilterCreator.getCreator();
		return true;
	}
	
	public boolean setSinkFilter(ISinkFilter filter)
	{
		m_sinkFilter = filter;
		if ( m_sinkFilter == null )
			m_sinkFilter = NullFilterCreator.getCreator();
		return true;
	}
	
	public boolean request(IAtomizable request, IReceiver replyHandler, IReceiver errorHandler) throws XubException
	{
		JxtaSocket socket = null;
		IAtomizable reply = null;

System.out.println("JxtaRequestHandler: sending request.");
		try
		{
			socket = new JxtaSocket(m_group, m_advertisement);
			MyReplyThread replyThread = new MyReplyThread(socket, replyHandler, errorHandler, m_pumpFilter);
			replyThread.start();
			java.io.OutputStream stream = socket.getOutputStream();
			IAtomizer output = m_sinkFilter.getInstance(stream);
			request.write(null, output);
			stream.flush();
//			stream.close();
		}
		catch ( Exception e )
		{
			e.printStackTrace();
			throw new XubException(e.getMessage());
		}
		finally
		{
			try
			{
			}
			catch (Exception e)
			{
			}
		}
		
		return true;
	}

	static class MyReplyThread extends Thread
	{
		private JxtaSocket		m_socket = null;
		private IPumpFilter		m_filter = null;
		private IReceiver		m_handler = null;
		private IReceiver		m_errorHandler = null;
		
		MyReplyThread (JxtaSocket socket, IReceiver receiver, IReceiver errorReceiver, IPumpFilter filter)
		{
			m_socket = socket;
			m_handler = receiver;
			m_errorHandler = errorReceiver;
			m_filter = filter;
		}
		
		public synchronized void run ()
		{
			if ( m_socket == null ) return;
			if ( m_filter == null ) return;
			if ( m_handler == null ) return;
			
			try
			{
				InputStream input = new java.io.BufferedInputStream(m_socket.getInputStream());
				IAtomizable message;
//				while ( true )
				{
					message = m_filter.getInstance(input);
//					if ( message == null )
//						break;
					m_handler.receive(message);
				}
			}
			catch ( Exception e )
			{
				System.out.println("MessageThread exception:");
				e.printStackTrace();
			}
			finally
			{
				try
				{
					m_socket.close();
				}
				catch ( Exception e )
				{
					//
				}
			}		
		}
	}
}












