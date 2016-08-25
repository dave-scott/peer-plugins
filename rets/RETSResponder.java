package com.haplos.xub.plugins.rets;

import com.haplos.xub.Connectable.IInitable;
import com.haplos.xub.Connectable.IResponder;
import com.haplos.xub.Connectable.ICloseable;
import com.haplos.xub.Connectable.ILogger;
import com.haplos.xub.Connectable.ILogHandler;
import com.haplos.xub.Connectable.StandardLogMessage;
import com.haplos.xub.Connectable.XubException;

import com.haplos.xub.Core.IAtomizer;
import com.haplos.xub.Core.IAtomizable;
import com.haplos.xub.Core.CacheAtomizable;
import com.haplos.xub.Toolkit.ComponentFactory;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import java.util.Iterator;

/*
 * by Dave Scott, 2005
 *
 * Copyright and license conditions are contained in the file named
 * 'LICENSE.TXT' which is distributed with this source code.  Online
 * copies of the license conditions can be found at
 *         http://haplos.com/licences/license.html
 */

/**
 * Class which takes RETS compact-decoded virtual xml stream and unpacks the poorly 
 * designed DATA element befor passing it on to the next atomizer. 
 * 
 * @author Dave Scott
 */

public class RETSResponder implements IResponder, IInitable, ICloseable, ILogger
{
	private Map			m_retsClients = null;
        private ILogHandler             m_logHandler = null;

	public RETSResponder()
	{
	}

	public boolean init(Map parameters)
	{
		String retsClassParam = ComponentFactory.getParameter("rets-class", "RES", parameters);
		String retsClasses[] = retsClassParam.split(",");
		for ( int i = 0; i < retsClasses.length; ++ i )
		{
			String retsClass = retsClasses[i];
			if ( retsClass.length() > 0 )
			{
				IResponder client = new RETSClient();
				if ( client instanceof IInitable )
				{
					parameters = new HashMap(parameters);
					parameters.put("rets-class", retsClass);
					((IInitable)client).init(parameters);
				}
				if ( m_retsClients == null )
					m_retsClients = new HashMap();
				m_retsClients.put(retsClass, client);
			}
		}
		
		return true;
	}

	public IAtomizable respond(IAtomizable message) throws XubException
	{
		return new MyRETSAtomizable(message, m_retsClients, m_logHandler);
	}
	
	public boolean close ()
	{
		m_retsClients = null;
		return true;
	}

        public boolean connect(ILogHandler handler)
        {
                m_logHandler = handler;
		Iterator responders = m_retsClients.keySet().iterator();
 		ILogger logger = null;
		Object classId = null;
		while ( responders.hasNext() )
		{
			classId = responders.next();
			logger = (ILogger)m_retsClients.get(classId);
			logger.connect(handler);
		}
		
               return true;
        }

	private static class MyRETSAtomizable implements IAtomizable
	{
		private IAtomizable		m_request = null;
		private Map			m_clients = null;
		private ILogHandler             m_logHandler = null;
		
		public MyRETSAtomizable(IAtomizable request, Map clients, ILogHandler handler)
		{
			m_clients = clients;
			m_logHandler = handler;
			if ( request == null )
				m_request = null;
			else if ( request.isReadOnce() )
				m_request = CacheAtomizable.copy(request);
			else
				m_request = request;
		}
		
		public boolean isReadOnce()
		{
			return false;
		}
		
		public boolean write(String name, IAtomizer output)
		{
			if ( output == null ) return true;
			
			if ( name == null )
				name = "rets-reply";
			output.open(name, null);
			
			if ( (m_request != null) && (m_clients != null) )
			{
				Iterator responders = m_clients.keySet().iterator();
				IResponder responder = null;
				Object classId = null;
				Map attributes = new HashMap();
				IAtomizable reply = null;
				
				while ( responders.hasNext() )
				{
					classId = responders.next();
					attributes.put("class-id", classId);
					output.open("rets-reply-set", attributes);
					responder = (IResponder)m_clients.get(classId);
					
					try
					{
						reply = responder.respond(m_request);
						reply.write(null, output);
					}
					catch (XubException xe)
					{
System.out.println("RETS Error: " + xe.getMessage());
//xe.printStackTrace();
						if ( m_logHandler != null )
							m_logHandler.log(new StandardLogMessage("RETSResponder", xe.getMessage()));
					}
					catch (Exception e)
					{
System.out.println("RETS Error: " + e.getMessage());
//e.printStackTrace();
					}
					output.close("rets-reply-set");
				}
			}
			
			output.close(name);
			return true;
		}
	}		
}



