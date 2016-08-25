package com.haplos.xub.plugins.rets;

import com.haplos.xub.Core.IAtomizerFilter;
import com.haplos.xub.Core.IAtomizer;
import com.haplos.xub.Core.StreamAtomizer;
import com.haplos.xub.Core.StreamAtomizable;
import com.haplos.xub.Connectable.ILogger;
import com.haplos.xub.Connectable.ILogHandler;
import com.haplos.xub.Toolkit.ComponentFactory;
import com.haplos.xub.Filters.IFilterCreator;

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
 * Class which takes RETS compact-decoded virtual xml stream and unpacks the poorly 
 * designed DATA element befor passing it on to the next atomizer. 
 * 
 * @author Dave Scott
 */

public class RETSAtomizerFilter implements IAtomizerFilter, IFilterCreator, ILogger
{
	private IAtomizer		m_output = null;
	private IAtomizer		m_peekOutput = null;
	private ILogHandler		m_logHandler = null;
	private long			m_recordCount = 0;

	private String			m_delimiter = "\t";
	private String[]		m_tags = null;
	private boolean			m_peekAtError = false;
	private boolean			m_retainEmptyValues = true;
	
	static private String		COLUMNS_TAG = "COLUMNS";
	static private String		DATA_TAG = "DATA";
	static private String		DELIMITER_TAG = "DELIMITER";
	static private String		DELIMITER_ATTR_TAG = "value";
	static private String		RETS_TAG = "RETS";
	
	public RETSAtomizerFilter(boolean peekAtError, boolean retainEmptyValues, ILogHandler handler)
	{
		m_peekAtError = peekAtError;
		m_retainEmptyValues = retainEmptyValues;
		connect(handler);
	}
	
	public IAtomizerFilter getInstance ()
	{
		return this;
	}
	
	public boolean connect(ILogHandler handler)
	{
		if ( m_logHandler == null )
			m_logHandler = handler;
		return true;
	}
	
	private boolean log(String message)
	{
		if ( m_logHandler != null )
		{
			message = "<RetsAtomizerFilter>" + message + "</RetsAtomizerFilter>";
			return m_logHandler.log(new StreamAtomizable(message));
		}
		return true;
	}

	public void setOutput (IAtomizer output)
	{
		m_output = output;
	}
	
	public boolean open (String name, Map attributes)
	{
		if ( m_output == null ) return true;
		if ( RETS_TAG.equals(name) )
		{
			if ( ComponentFactory.getIntParameter("ReplyCode", 0, attributes) != 0 )
			{
				if ( m_peekAtError )
					m_peekOutput = new StreamAtomizer(System.out);
				log("Rets error: " + ComponentFactory.getParameter("ReplyCode", "???", attributes) + ": " + ComponentFactory.getParameter("ReplyText", "???", attributes));
			}
		}
		
		if ( m_peekOutput != null ) return m_peekOutput.open(name, attributes);
		return m_output.open(name, attributes);
	}
	
	public boolean leaf (String name, Map attributes, Object value)
	{
		if ( m_output == null ) return true;
		if ( RETS_TAG.equals(name) )
		{
			if ( ComponentFactory.getIntParameter("ReplyCode", 0, attributes) != 0 )
			{
				if ( m_peekAtError )
				{
					m_peekOutput = new StreamAtomizer(System.out);
					m_peekOutput.leaf(name, attributes, value);
					m_peekOutput = null;
				}
				log("Rets error: " + ComponentFactory.getParameter("ReplyCode", "???", attributes) + ": " + ComponentFactory.getParameter("ReplyText", "???", attributes));
			}
		}

		if ( m_peekOutput != null ) return m_peekOutput.leaf(name, attributes, value);

		if ( DATA_TAG.equals(name) )
		{
			if ( m_tags != null )
			{
				if ( value != null )
				{
					m_output.open("result", null);		// To have same flush conditions as JDBCAtomizable
					String[]	values = value.toString().split(m_delimiter);
					int		n = 1;
					int		stop = values.length;
					
					if ( m_tags.length < stop )
						stop = m_tags.length;
				
					while ( n < stop )
					{
						if ( m_tags[n].length() > 0 )
						{
							if ( m_retainEmptyValues || (values[n].length() > 0) )
								m_output.leaf(m_tags[n], null, values[n]);
						}
						++n;
					}
					m_output.close("result");
				}
			}
			++m_recordCount;
		}
		else if ( COLUMNS_TAG.equals(name) )
		{
			if ( value != null )
				m_tags = value.toString().split(m_delimiter);
		}
		else if ( DELIMITER_TAG.equals(name) )
		{
			if ( attributes != null )
			{
				int del = ComponentFactory.getIntParameter(DELIMITER_TAG, '\t', attributes);
				m_delimiter.replace(m_delimiter.charAt(0), (char)del);
			}
		}
		return true;
	}
	
	public boolean close (String name)
	{
		if ( m_output == null ) return true;
		
		if ( RETS_TAG.equals(name) )
		{
			System.out.flush();
			log ("Received " + String.valueOf(m_recordCount) + " records.");
		}

		if ( m_peekOutput != null )
		{
			m_peekOutput.close(name);
			if ( RETS_TAG.equals(name) )
				m_peekOutput = null;
			return true;
		}
		
		return m_output.close(name);
	}
}

