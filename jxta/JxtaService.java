package com.haplos.xub.plugins.jxta;

import com.haplos.xub.Connectable.IInitable;
import com.haplos.xub.Toolkit.ComponentFactory;

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
 * Class for starting jxta service.
 * 
 * @author Dave Scott
 */
 
public class JxtaService implements IInitable
{
	public JxtaService()
	{
	}
	
	public boolean init(Map parameters)
	{
		JxtaUtilities.startJxta();
		if ( ComponentFactory.getBooleanParameter("rendezvous", true, parameters) )
			JxtaUtilities.startGroupRendezvous(parameters);
		return true;
	}
}

