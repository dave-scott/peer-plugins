package com.haplos.xub.plugins.jxta;

import com.haplos.xub.Core.IAtomizable;
import com.haplos.xub.Core.IAtomizer;
import com.haplos.xub.Connectable.IInitable;
import com.haplos.xub.Connectable.IReceiver;
import com.haplos.xub.Connectable.XubException;
import com.haplos.xub.Filters.ISink;
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

public class JxtaReceiver implements IInitable, ISink, IReceiver
{
	private PeerGroup		m_group = null;
	private PipeAdvertisement	m_advertisement = null;

	private ISinkFilter		m_sinkFilter = NullFilterCreator.getCreator();

	public JxtaReceiver()
	{
	}
	
	public boolean init(Map parameters)
	{
		m_group = JxtaUtilities.getGroup(parameters);
		m_advertisement = JxtaUtilities.getPipeAdvertisement(parameters);
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
		JxtaSocket socket = null;
		boolean success = false;
		
		try
		{
			socket = new JxtaSocket(m_group, m_advertisement);
			IAtomizer output = m_sinkFilter.getInstance(socket.getOutputStream());
			success = message.write("REQUEST", output);
		}
		catch ( Exception e )
		{
			throw new XubException(e.getMessage());
		}
		finally
		{
			try
			{
				if ( socket != null )
					socket.close();
			}
			catch(Exception e)
			{
			}
		}
		
		return success;
	}
}












