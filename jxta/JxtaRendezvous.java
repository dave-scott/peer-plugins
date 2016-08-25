package com.haplos.xub.plugins.jxta;

import com.haplos.xub.Connectable.IInitable;

import net.jxta.peergroup.PeerGroup;

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
 * Class which starts jxta rendezvous service for a group.
 * 
 * @author Dave Scott
 */

public class JxtaRendezvous implements IInitable
{

	public JxtaRendezvous()
	{
	}
	
	public boolean init(Map parameters)
	{
		PeerGroup group = JxtaUtilities.startGroupRendezvous(parameters);
		return group != null;
	}
}

