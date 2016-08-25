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
import net.jxta.socket.JxtaServerSocket;

import java.net.Socket;
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

public class JxtaSender implements ISender, IInitable, IPump, ICloseable
{
	private Thread 			m_serverThread = null;
	private PeerGroup		m_group = null;
	private PipeAdvertisement	m_advertisement = null;
	private IPumpFilter		m_filter = NullFilterCreator.getCreator();
	private ISendHandler		m_handler = null;

	public JxtaSender()
	{
	}
	
	public boolean init(Map parameters)
	{
		m_group = JxtaUtilities.getGroup(parameters);
		m_advertisement = JxtaUtilities.getPipeAdvertisement(parameters);
		return true;
	}
	
	public synchronized boolean close()
	{
		if ( m_serverThread != null )
			m_serverThread.interrupt();
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
		System.out.println ("Opening SocketSender.");
		start();
		return true;
	}
	
	public void start()
	{
		try
		{
			m_serverThread = new Thread(new Runnable()
				{
					public synchronized void run()
					{
						JxtaServerSocket serverSocket = null;
						try
						{
							serverSocket = new JxtaServerSocket(m_group, m_advertisement);
							while ( ! m_serverThread.isInterrupted() )
							{
								Socket messageSocket = serverSocket.accept();
								Thread messageThread = new MessageThread(messageSocket, m_filter, m_handler);
								messageThread.start();
							}
						}
						catch ( Exception e)
						{
							//
						}
						finally
						{
							try
							{
								serverSocket.close();
							}
							catch (Exception e)
							{
							}
						}		
					}	
				});
		
			// Start running my server's thread.
			m_serverThread.start();
		}
		catch ( Exception e )
		{
			e.printStackTrace();
		}
	}
	
	static class MessageThread extends Thread
	{
		private Socket			m_socket = null;
		private IPumpFilter		m_filter = null;
		private ISendHandler		m_handler = null;
		
		MessageThread (Socket socket, IPumpFilter filter, ISendHandler handler)
		{
			m_socket = socket;
			m_filter = filter;
			m_handler = handler;
		}
		
		public synchronized void run ()
		{
			if ( m_socket == null ) return;
			if ( m_filter == null ) return;
			if ( m_handler == null ) return;
			
			try
			{
				m_handler.send(m_filter.getInstance(m_socket.getInputStream()));
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












