package com.haplos.xub.plugins.jxta;

import com.haplos.xub.Core.IAtomizer;
import com.haplos.xub.Core.IAtomizable;
import com.haplos.xub.Connectable.IInitable;
import com.haplos.xub.Connectable.ICloseable;
import com.haplos.xub.Connectable.IRequester;
import com.haplos.xub.Connectable.IReceiver;
import com.haplos.xub.Connectable.IRequestHandler;
import com.haplos.xub.Connectable.XubException;
import com.haplos.xub.Filters.IPump;
import com.haplos.xub.Filters.ISink;
import com.haplos.xub.Filters.IPumpFilter;
import com.haplos.xub.Filters.ISinkFilter;
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
 * Class which listens on a port for requests for a socket connection; new connections are given a
 * new thread which forwards incoming messages to the SyncAsyncBridge.
 * 
 * @author Dave Scott
 */

public class JxtaRequester implements ICloseable, IInitable, IPump, ISink, IRequester
{
	private Thread 			m_serverThread = null;
	private PeerGroup		m_group = null;
	private PipeAdvertisement	m_advertisement = null;
	private int			m_timeout = 60;

	private IPumpFilter		m_pumpFilter = NullFilterCreator.getCreator();
	private ISinkFilter		m_sinkFilter = NullFilterCreator.getCreator();
	private IRequestHandler		m_handler = null;

	public JxtaRequester()
	{
	}
	
	public boolean init(Map parameters)
	{
		m_group = JxtaUtilities.getGroup(parameters);
		m_advertisement = JxtaUtilities.getPipeAdvertisement(parameters);
		start();
		return true;
	}
	
	public boolean setPumpFilter(IPumpFilter filter)
	{
System.out.println("SocketRequester: Setting pump filter.");
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
	
	public boolean connect(IRequestHandler handler)
	{
		m_handler = handler;
		return true;
	}
	
	public synchronized boolean close()
	{
		if ( m_serverThread != null )
			m_serverThread.interrupt();
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
						boolean doContinue = true;
						
						while ( doContinue )
						{
							doContinue = false;
							try
							{
								serverSocket = new JxtaServerSocket(m_group, m_advertisement);
								while ( ! m_serverThread.isInterrupted() )
								{
									Socket messageSocket = serverSocket.accept();
System.out.println("JxtaRequester: connection made.");
									Thread messageThread = new MessageThread(messageSocket, m_handler, m_pumpFilter, m_sinkFilter, m_timeout);
									messageThread.start();
								}
							}
							catch ( java.net.SocketTimeoutException ste )
							{
System.out.println("JxtaRequester: " + ste.getMessage());
								doContinue = true;
							}
							catch ( Exception e)
							{
								e.printStackTrace();
							}

							try
							{
								if ( serverSocket != null )
									serverSocket.close();
							}
							catch (Exception e)
							{
								System.out.println("JxtaRequester: " + e.getMessage());
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
	
	static class MessageThread extends Thread implements IReceiver
	{
		private Socket			m_socket = null;
		private IRequestHandler		m_handler = null;
		private IPumpFilter		m_pumpFilter = null;
		private ISinkFilter		m_sinkFilter = null;
		private int			m_timeout;
		private Object			m_lock = new Object();
		
		public MessageThread (Socket socket, IRequestHandler handler, IPumpFilter pumpFilter, ISinkFilter sinkFilter, int timeout)
		{
			m_socket = socket;
			m_handler = handler;
			m_pumpFilter = pumpFilter;
			m_sinkFilter = sinkFilter;
			m_timeout = timeout;
		}
		
		public boolean close()
		{
			try
			{
				synchronized(m_lock)
				{
					if ( m_socket != null )
					{
						m_socket.close();
						m_socket = null;
					}
				}
			}
			catch(Exception e)
			{
			}
			return true;
		}
		
		public boolean receive(IAtomizable message) throws XubException
		{
			if ( message == null ) return true;
			boolean success = false;
			
			try
			{
				synchronized(m_lock)
				{
					if ( m_socket != null )
					{
//						java.io.OutputStream stream = new java.io.BufferedOutputStream(m_socket.getOutputStream());
						java.io.OutputStream stream = m_socket.getOutputStream();
						IAtomizer output = m_sinkFilter.getInstance(stream);
						success = message.write("REPLY", output);
						stream.flush();
	//					stream.close();
					}
					else
						throw(new XubException("Socket timed out."));
				}
			}
			catch ( Exception e )
			{
				e.printStackTrace();
				throw new XubException(e.getMessage());
			}
			finally
			{
				close();
			}
			
			return success;
		}
		
		public synchronized void run ()
		{
			if ( m_socket == null ) return;
			if ( m_handler == null ) return;
			if ( m_pumpFilter == null ) return;
			if ( m_sinkFilter == null ) return;
			
			try
			{
				m_handler.request(m_pumpFilter.getInstance(new java.io.BufferedInputStream(m_socket.getInputStream())), this, null);
				if ( m_socket != null )
					sleep(m_timeout*1000);
//System.out.println("Returning.");
			}
			catch ( Exception e )
			{
				System.out.println("MessageThread exception:");
				e.printStackTrace();
			}
			finally
			{
//				close();
			}		
		}
	}
}












