package com.haplos.xub.plugins.rets;

import java.util.Map;
import java.util.List;
import java.util.Iterator;
import java.util.Vector;

import com.haplos.xub.Core.IAtomizer;
import com.haplos.xub.Core.IAtomizable;

/*
 * by Dave Scott, 2005
 *
 * Copyright and license conditions are contained in the file named
 * 'LICENSE.TXT' which is distributed with this source code.  Online
 * copies of the license conditions can be found at
 *         http://haplos.com/licences/license.html
 */

 /**
 * Class for converting search request message into a RETS DMQL statement.
 * @author Dave Scott
 */
public class ProfileToDMQL implements IAtomizer
{
	private List		m_path = null;
	private boolean		m_buildSearch = false;
	private int		m_index = 0;
	private String		m_field = null;
	private String		m_tables = "";
	private String		m_outputFields = "";
	private String		m_fixedTerms = "";

	private String		m_comparand = null;
	private String		m_expression = null;
	
	ProfileToDMQL()
	{
	}
	
	public void reset ()
	{
		m_path = null;
		m_index = 0;
	}
	
	public String getSearchExpression ()
	{
		if ( m_tables.length() == 0 ) return "";
		
		String		path = "";
		String connector = "";
		
		if ( m_path != null )
		{
			Iterator	terms = m_path.iterator();
			String		term = null;
			while ( terms.hasNext() )
			{
				term = terms.next().toString();
				if ( term.length() > 0 )
				{
					path += connector;
					connector = ",";
					path += term;
				}
			}
		}
			
		if ( m_fixedTerms.length() > 0 )
		{
			path += connector;
			path += m_fixedTerms;
		}
				
		return path;
	}
	
	public String getOutputFields ()
	{
		return m_outputFields;
	}
	
	public String getTables ()
	{
		if ( m_tables.length() == 0 ) return null;
		return m_tables;
	}
	
	private boolean handleEnumeration (Object value)
	{
		if ( m_comparand == null ) return false;
		if ( value == null ) return true;
		if ( value.toString().length() == 0 ) return true;
		
		if ( m_expression == null )
			m_expression = "";
		else
			m_expression += ",";
		
		m_expression += convertType("string", value);
		return true;
	}
	
	private boolean handleMessage (String name, Map attributes)
	{
		if ( m_path == null ) m_path = new Vector ();
		
		if ( m_path.size() < m_index )
			m_path.add ("");

		if ( attributes == null ) return true;
		
		String term = (String)m_path.get(m_index - 1);
		
		m_comparand = m_field;

		String expression = "";
		String type = (String)attributes.get("type");
		if ( type == null )		// Set the default type for arguments
			type="string";		// to "string"
		
		String expOp = null;
		String op = "";
		if ( term.length() > 0 )
			op = ",";

		Object		value = attributes.get("match");
		if ( (value != null) && (value.toString().length() > 0) )
		{
			expression = op + convertType(type, value);
			op = ",";
			expOp = ((expOp == null) ? "=" : "=+");
		}

		value = attributes.get("excluding");
		if ( (value != null) && (value.toString().length() > 0) )
		{
			expression += op + "~" + convertType(type, value);
			op = ",";
			expOp = ((expOp == null) ? "=" : "=+");
		}
		
		if ( type == null )		// The default type for the remaining operators
			type="number";		// is "number"
			
		Object min = attributes.get("minInclusive");
		Object minExc = null;
		if ( min == null )
		{
			min  = attributes.get("minExclusive");
			minExc = min;
		}
			
		Object max = attributes.get("maxInclusive");
		Object maxExc = null;
		if ( max == null )
		{
			max  = attributes.get("maxExclusive");
			maxExc = max;
		}
		
		if ( (min != null) && (min.toString().length() > 0) )
		{
			expression += op + convertType(type, min);
			if ( max == null )
			{
				expression += "+";
				expOp = ((expOp == null) ? "=" : "=+");
			}
			else
				expression += "-";
			op = ",";
		}

		if ( (max != null) && (max.toString().length() > 0) )
		{
			if ( min == null )
				expression += op + convertType(type, max) + "-";
			else
				expression += convertType(type, max);
			op = ",";
			expOp = ((expOp == null) ? "=" : "=+");
		}

		if ( (minExc != null) && (minExc.toString().length() > 0) )
		{
			expression += op + "~" + convertType(type, minExc);
			op = ",";
			expOp = ((expOp == null) ? "=" : "=+");
		}

		if ( (maxExc != null) && (maxExc.toString().length() > 0) )
		{
			expression += op + "~" + convertType(type, maxExc);
			op = ",";
			expOp = ((expOp == null) ? "=" : "=+");
		}

		if ( expression.length() > 0 )
			term += "(" + m_comparand + expOp + expression + ")";
		m_path.set (m_index-1,term);
		return true;
	}
	
	public boolean open (String name, Map attributes)
	{
		if ( m_buildSearch )
		{
			if ( ! (name.equals("swquery:attribute") || name.equals("swquery:enumeration")) )
			{
				++m_index;
				if ( name.equals("swquery:node") )
				{
					m_field = (String)attributes.get("name");
					if ( m_field == null ) return false;
				}
				else
					m_field = name;
			}
			return handleMessage (name, attributes);
		}

		if ( name.equals ("swquery:search-criteria") )
			m_buildSearch = true;

		return true;
	}

	public boolean leaf (String name, Map attributes, Object value)
	{
		if ( name.equals ("swquery:search-table") )
		{
			if ( m_tables.length() > 0 )
				m_tables += ",";
			if ( value != null )
				m_tables += value.toString();
		}
		else if ( name.equals ("swquery:return-field") )
		{
			if ( m_outputFields.length () > 0 )
				m_outputFields += ",";
			if ( value != null )
				m_outputFields += value;
		}
		else if ( name.equals ("swquery:fixed-term") )
		{
			if ( m_fixedTerms.length () > 0 )
				m_fixedTerms += " ";
			if ( value != null )
				m_fixedTerms += value;
		}
		else if ( m_buildSearch )
		{
			if ( name.equals ("swquery:enumeration") )
			{
				if ( (value == null) && (attributes != null) )
				    value = attributes.get("value");
				handleEnumeration (value);
			}
			else
			{
				open (name, attributes);
				close (name);
			}
		}
		return true;
	}
	
	public boolean close (String name)
	{
		if ( name.equals ("swquery:search-criteria") )
			m_buildSearch = false;

		if ( m_index > 0 )
		{
			if ( m_expression != null )		// an enumeration
			{
				String term = (String)m_path.get (m_index - 1);
				if ( (term != null) && (term.length() > 0) )
					term += ",";
				term += "(" + m_comparand + "=|" + m_expression + ")" ;
				m_path.set (m_index-1,term);
				m_expression = null;
			}
//			if ( ! (name.equals("swquery:attribute") || name.equals("swquery:enumeration")) )
//				--m_index;
		}
		return true;
	}

	private Object convertType(String type, Object value)
	{
//		if ( type == null ) return "'" + scrub(value.toString()) + "'";
//		if ( type.equals("string") ) return "'" + scrub(value.toString()) + "'";

		return value;
	}
	
	private String scrub(String value)
	{
		if ( value == null ) return null;

		int j = value.indexOf("'");
		if ( j == -1 ) return value;
		
		StringBuffer buffer = new StringBuffer(value);
		
		while ( j > -1 )
		{
			buffer = buffer.replace(j, j+ 1, "''");
			j = buffer.indexOf("'", j+2);
		}
		
		return buffer.toString();
	}
		
	public static void main (String args[])
	{
		String xmlString = "<swquery:search-request>";
		xmlString += "<swquery:search-table>cie</swquery:search-table>";
		xmlString += "<swquery:search-table>type</swquery:search-table>";
		xmlString += "<swquery:search-table>subtype</swquery:search-table>";
		xmlString += "<swquery:search-table>property</swquery:search-table>\n";
		xmlString += "<swquery:fixed-term>,(price=600000+)</swquery:fixed-term>";
//		xmlString += "<fixed-term>type.subtype_id=subtype.id</fixed-term>";
//		xmlString += "<fixed-term>subtype.property_id=property.id</fixed-term>\n";
		xmlString += "<swquery:search-criteria>";
		xmlString += "<cie>\n";
		xmlString += "<attribute name='id' value='industrial' />\n";
		xmlString += "<type><swquery:attribute name='id'><swquery:enumeration>T1</swquery:enumeration><swquery:enumeration>ST2</swquery:enumeration></swquery:attribute></type>\n";
		xmlString += "<subtype><swquery:attribute name='id'><swquery:enumeration>ST1</swquery:enumeration><swquery:enumeration>ST2</swquery:enumeration></swquery:attribute></subtype>\n";
		xmlString += "<property excluding='myProperty'>\n";
//		xmlString += "<attribute name='price' minInclusive='100000' maxExclusive='200000' />\n";
		xmlString += "</property>\n";
//		xmlString += "</subtype>\n";
//		xmlString += "</type>\n";
		xmlString += "</cie>";
		xmlString += "</swquery:search-criteria>";
		xmlString += "</swquery:search-request>";

		System.out.println ("FROM:");
		System.out.println (xmlString);
		System.out.println ("\nTO:");

		IAtomizable		atomizable = new com.haplos.xub.Core.StreamAtomizable (xmlString);
		ProfileToDMQL	atomizer = new ProfileToDMQL();
		atomizable.write ("xpath-req", atomizer);
		System.out.println (atomizer.getSearchExpression());
	}
}

