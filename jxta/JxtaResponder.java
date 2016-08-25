package com.haplos.xub.plugins.jxta;

import com.haplos.xub.Core.IAtomizable;
import com.haplos.xub.Core.IAtomizer;
import com.haplos.xub.Connectable.IInitable;
import com.haplos.xub.Connectable.IResponder;
import com.haplos.xub.Connectable.XubException;
import com.haplos.xub.Filters.IPump;
import com.haplos.xub.Filters.ISink;
import com.haplos.xub.Filters.IPumpFilter;
import com.haplos.xub.Filters.ISinkFilter;
import com.haplos.xub.Filters.Null.NullFilterCreator;
import com.haplos.xub.Toolkit.ComponentFactory;

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
 * Class which listens on a port for requests for a socket connection; new connections are given a
 * new thread which forwards incoming messages to the SyncAsyncBridge.
 * 
 * @author Dave Scott
 */

public class JxtaResponder implements IInitable, IPump, ISink, IResponder
{
	private PeerGroup		m_group = null;
	private PipeAdvertisement	m_advertisement = null;
	private int			m_timeout = 30;

	private IPumpFilter		m_pumpFilter = NullFilterCreator.getCreator();
	private ISinkFilter		m_sinkFilter = NullFilterCreator.getCreator();

	public JxtaResponder()
	{
	}
	
	public boolean init(Map parameters)
	{
		m_group = JxtaUtilities.getGroup(parameters);
		m_advertisement = JxtaUtilities.getPipeAdvertisement(parameters);
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
	
	public IAtomizable respond(IAtomizable message) throws XubException
	{
		JxtaSocket socket = null;
		IAtomizable reply = null;
		
		try
		{
			socket = new JxtaSocket(m_group, m_advertisement);
System.out.println("JxtaResponder: connection made.  sending ...");
			IAtomizer output = m_sinkFilter.getInstance(socket.getOutputStream());
			if ( message.write("REQUEST", output) )
				reply = m_pumpFilter.getInstance(socket.getInputStream());
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
//				if ( socket != null )
//					socket.close();
			}
			catch (Exception e)
			{
			}
		}
		
		return reply;
	}
}












