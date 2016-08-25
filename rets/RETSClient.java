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
import com.haplos.xub.Core.StreamAtomizable;
import com.haplos.xub.Core.StreamAtomizer;
import com.haplos.xub.Core.AtomizableFilter;
import com.haplos.xub.Toolkit.ComponentFactory;
import com.haplos.xub.Filters.Utility.ReadLimitFilter;
import com.haplos.xub.plugins.Http.HTTPHeader;
import com.haplos.xub.plugins.Http.HTTPChunkedBodyFilter;

import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.net.Socket;
import java.net.InetAddress;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.security.MessageDigest;

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

public class RETSClient implements IResponder, IInitable, ICloseable, ILogger
{
	private String		m_searchUri = null;
	private String		m_loginUri = null;
	private String		m_logoutUri = null;
	private String		m_actionUri = null;
	private String		m_clientType = "query";
	private String		m_user = "";
	private String		m_password = "";
	private String		m_userAgentPassword = null;
	private String		m_host;
	private String		m_retsRequestID = "";
	private String		m_sessionID = "";
	private InetAddress	m_ip;
	private int		m_port;
	private boolean		m_debug;
	private boolean		m_keepAlive = false;
	
	private static String	NC = "00000001";
	private static String	CNONCE = "feebbeef";
	
	private String		m_defaultClass = null;
	private String		m_defaultQuery = null;
	private String		m_defaultFields = null;
	private String		m_defaultLimit = null;
	private String		m_userAgent = null;
	private String		m_retsVersion = null;
	private String		m_retsResource = "Property";
	private String		m_searchMethod = null;
	private String		m_loginMethod = null;
	private String		m_authMethod = "Digest";
	
	private boolean		m_peek = false;
	private boolean		m_replyDebug = false;
	private boolean		m_retainEmptyValues = false;
	private boolean		m_displayHeaders = false;
	private ILogHandler	m_logHandler = null;
	private long		m_errorBucket = 0;
	
	public RETSClient()
	{
	}
	
	public boolean init(Map parameters)
	{
		m_host = ComponentFactory.getParameter(ComponentFactory.HOST, null, parameters);
		try
		{
			m_ip = InetAddress.getByName(m_host);
		}
		catch (Exception e)
		{
System.out.println("Could not resolve IP address for " + m_host);
			return false;
		}
		m_clientType = ComponentFactory.getParameter("client-type", m_clientType, parameters);
		m_searchUri = ComponentFactory.getParameter("search-uri", null, parameters);
		if (m_searchUri == null ) return false;
		m_loginUri = ComponentFactory.getParameter("login-uri", null, parameters);
		if (m_loginUri == null ) return false;
		m_logoutUri = ComponentFactory.getParameter("logout-uri", null, parameters);
		m_actionUri = ComponentFactory.getParameter("action-uri", null, parameters);
		m_user = ComponentFactory.getParameter(ComponentFactory.USER, "", parameters);
		m_password = ComponentFactory.getParameter(ComponentFactory.PASSWORD, "", parameters);
		m_port = ComponentFactory.getIntParameter(ComponentFactory.PORT, 80, parameters);
		m_debug = ComponentFactory.getBooleanParameter("debug", false, parameters);

		m_retsResource = ComponentFactory.getParameter("rets-resource", m_retsResource, parameters);
		m_defaultClass = ComponentFactory.getParameter("rets-class", null, parameters);
		m_defaultQuery = ComponentFactory.getParameter("rets-query", null, parameters);
		m_defaultFields = ComponentFactory.getParameter("rets-output", null, parameters);
		m_defaultLimit = ComponentFactory.getParameter("rets-limit", null, parameters);
		m_retsVersion = ComponentFactory.getParameter("rets-version", "RETS/1.0", parameters);
		m_userAgent = ComponentFactory.getParameter("user-agent", "Dataweb/1.0", parameters);
		m_userAgentPassword = ComponentFactory.getParameter("user-agent-password", null, parameters);
		m_authMethod = ComponentFactory.getParameter("auth-method", "Digest", parameters).toUpperCase();
		m_searchMethod = ComponentFactory.getParameter("search-method", "GET", parameters).toUpperCase();
		m_loginMethod = ComponentFactory.getParameter("login-method", "GET", parameters).toUpperCase();
		m_peek = ComponentFactory.getBooleanParameter("error-peek", false, parameters);
		m_replyDebug = ComponentFactory.getBooleanParameter("reply-debug", false, parameters);
		m_displayHeaders = ComponentFactory.getBooleanParameter("display-headers", false, parameters);
		
//		if ( m_userAgentPassword != null )
//			m_retsRequestID = CNONCE;
		return true;
	}

	public boolean connect(ILogHandler handler)
	{
		if ( m_logHandler == null )
		{
			m_logHandler = handler;
		}
		return true;
	}
	
	public IAtomizable respond(IAtomizable message) throws XubException
	{
		MyLogHandler logHandler = new MyLogHandler(m_logHandler, m_host);
		try
		{
			Socket s = new Socket(m_ip, m_port);
			
			HTTPHeader requestHeader = new HTTPHeader();
			m_sessionID = "";
			requestHeader.setHeaderAttribute(HTTPHeader.URI, m_loginUri);
			requestHeader.setHeaderAttribute("User-Agent", m_userAgent);
			requestHeader.setHeaderAttribute("Accept", "*/*");
			requestHeader.setHeaderAttribute(HTTPHeader.METHOD, m_loginMethod);
			if ( m_port != 80 )
				requestHeader.setHeaderAttribute("Host", m_host + ":" + m_port);
			else
				requestHeader.setHeaderAttribute("Host", m_host);
			requestHeader.setHeaderAttribute("Rets-Version", m_retsVersion);
			if ( m_retsRequestID.length() > 0 )
				requestHeader.setHeaderAttribute("RETS-Request-ID", m_retsRequestID);
				
			if ( m_keepAlive )
				requestHeader.setHeaderAttribute("Connection", "keep-alive");
			MyRETSCallRecord result = sendRetsRequest(s, requestHeader, null);
// send login
			InputStream input = result.getPayloadInputStream();
			if ( "200".equals(result.getReplyHeader().getHeaderAttribute(HTTPHeader.STATUS_CODE)) )
			{
				if ( m_displayHeaders )
					new StreamAtomizable(input).write(null, new StreamAtomizer(System.out));
				else
				{
					for ( int c = input.read(); c != -1; c  = input.read() );
				}
			}
			else
			{
				System.out.println("RETS Connection Failure: " + result.getReplyHeader().getHeaderAttribute(HTTPHeader.STATUS_MSG));
				for ( int c = input.read(); c != -1; c  = input.read() )
					System.out.write(c);
				System.out.write('\n');
			}
			
			if ( ! m_keepAlive )
			{
				result.getSocket().close();
				s = new Socket(m_ip, m_port);
			}

			if ( m_actionUri != null )
			{
				requestHeader.setHeaderAttribute(HTTPHeader.URI, m_actionUri);
				result = sendRetsRequest(s, requestHeader, null);
// send action
				input = result.getPayloadInputStream();
				if ( "200".equals(result.getReplyHeader().getHeaderAttribute(HTTPHeader.STATUS_CODE)) )
					new StreamAtomizable(input).write(null, new StreamAtomizer(System.out));
				else
				{
					System.out.println("RETS Connection Failure: " + result.getReplyHeader().getHeaderAttribute(HTTPHeader.STATUS_MSG));
					for ( int c = input.read(); c != -1; c  = input.read() )
						System.out.write(c);
					System.out.write('\n');
				}
				
				if ( ! m_keepAlive )
				{
					result.getSocket().close();
					s = new Socket(m_ip, m_port);
				}
			}
// send query			
			String queryString = "";
			if ( "query".equals(m_clientType) )
				queryString = buildQueryRequest(message);
			else if ( "metadata".equals(m_clientType) )
				queryString = buildMetadataRequest(message);
			logHandler.log(queryString);
			
			requestHeader.setHeaderAttribute(HTTPHeader.METHOD, m_searchMethod);
			requestHeader.setHeaderAttribute("Authorization", null);
			
			if ( "GET".equals(m_searchMethod) )
			{
				requestHeader.setHeaderAttribute(HTTPHeader.URI, m_searchUri+"?"+queryString);
				result = sendRetsRequest(s, requestHeader, null);
			}
			else
			{
				requestHeader.setHeaderAttribute(HTTPHeader.URI, m_searchUri);
				requestHeader.setHeaderAttribute("Content-Length", String.valueOf(queryString.length()));
				result = sendRetsRequest(s, requestHeader, queryString);
			}
			
			input = result.getPayloadInputStream();
			if ( ! "200".equals(result.getReplyHeader().getHeaderAttribute(HTTPHeader.STATUS_CODE)) )
			{
				for ( int c = input.read(); c != -1; c  = input.read() )
					System.out.write(c);
				System.out.write('\n');
				result.getSocket().close();
				throw new XubException("RETS Connection Failure: " + result.getReplyHeader().getHeaderAttribute(HTTPHeader.STATUS_MSG)); 
			}
			

			if ( m_replyDebug )
			{
				java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
				for ( int c = input.read(); c != -1; c  = input.read() )
					baos.write(c);
				System.out.write(baos.toByteArray());
				System.out.write('\n');
				input = new java.io.ByteArrayInputStream(baos.toByteArray());
			}
			
			return new AtomizableFilter(new RETSAtomizerFilter(m_peek, m_retainEmptyValues, logHandler), new StreamAtomizable(input));
		}
		catch (Exception e)
		{
			logHandler.log(e.getMessage());
			throw new XubException(e.getMessage());
		}
	}
	
	public String buildQueryRequest(IAtomizable message)
	{
		String queryString = "Format=COMPACT-DECODED&SearchType=" + m_retsResource;

		if ( ! "RETS/1.0".equals(m_retsVersion) )
			queryString += "&QueryType=DMQL2&Class=";	
		else
			queryString += "&QueryType=DMQL&Class=";
		
		
		if ( m_defaultClass != null )
			queryString += m_defaultClass;
		if ( m_defaultQuery == null )
		{
			ProfileToDMQL translator = new ProfileToDMQL();
			message.write(null, translator);
			if ( m_defaultClass == null )
				queryString += translator.getTables();

			String select = translator.getOutputFields();
			if ( (select != null) && select.length() > 0 )
			{
				queryString += "&Select=";
				queryString += select;
			}
			queryString += "&Query=";
			queryString += translator.getSearchExpression();
		}
		else
		{
			queryString += "&Query=" + m_defaultQuery;
			if ( m_defaultFields != null )
			{
				queryString += "&Select=";
				queryString += m_defaultFields;
			}
		}
		if ( m_defaultLimit != null )
		{
			queryString += "&Limit=";
			queryString += m_defaultLimit;
		}
		
		return queryString;
	}
	
	public String buildMetadataRequest(IAtomizable message)
	{
		String metadataId = "0";
		String metadataType = "METADATA-RESOURCE";

		if ( message != null )
		{
			MyMetadataParamExtractor extractor = new MyMetadataParamExtractor();
			message.write(null, extractor);
			metadataId = extractor.getId();
			metadataType = extractor.getType();
		}
		
		return "Format=COMPACT&Type=" + metadataType + "&ID=" + metadataId;
	}

	public boolean close ()
	{
		m_host = null;
		return true;
	}

	private MyRETSCallRecord sendRetsRequest(Socket s, HTTPHeader requestHeader, String queryString) throws XubException
	{
		try
		{
			OutputStream output = s.getOutputStream();
			requestHeader.setHeaderAttribute("RETS-UA-Authorization", getUAAuthorizationString());
			requestHeader.setHeaderAttribute("Authorization", getBasicAuthorizationString());
			
			requestHeader.writeHeader(output);
			if ( (queryString != null) && (queryString.length() > 0) )
				output.write(queryString.getBytes());
			output.flush();
			
			if ( m_displayHeaders )
			{
				System.out.println("***** Sent *****");
				requestHeader.writeHeader(System.out);
				if ( queryString != null ) System.out.println(queryString + "\n");
			}
		
			HTTPHeader replyHeader = new HTTPHeader();
			InputStream input = s.getInputStream();
			replyHeader.readHeader(input);
			
			if ( m_displayHeaders )
			{
				System.out.println("***** Received *****");
				replyHeader.writeHeader(System.out);
			}

			addCookies(extractCookies(null, replyHeader), requestHeader);
		
// should get authentication failure
			if ( "401".equals(replyHeader.getHeaderAttribute(HTTPHeader.STATUS_CODE)) )
			{
// send login request
				if ( ! m_keepAlive )
				{
					s.close();
					s = new Socket(m_ip, m_port);
				}
				String seed = replyHeader.getHeaderAttribute("WWW-Authenticate");
				requestHeader.setHeaderAttribute("Authorization", getAuthorizationString(seed, requestHeader.getHeaderAttribute(HTTPHeader.URI),requestHeader.getHeaderAttribute(HTTPHeader.METHOD)));
				m_sessionID = replyHeader.getCookieParameter("RETS-Session-ID");
				if ( m_sessionID == null ) m_sessionID = "";
				requestHeader.setHeaderAttribute("RETS-UA-Authorization", getUAAuthorizationString());
				
				output = s.getOutputStream();
				requestHeader.writeHeader(output);
				if ( (queryString != null) && (queryString.length() > 0) )
					output.write(queryString.getBytes());
				output.flush();
				
				if ( m_displayHeaders )
				{
					System.out.println("***** Sent *****");
					requestHeader.writeHeader(System.out);
					if ( queryString != null ) System.out.println(queryString);
				}
	
			
				replyHeader = new HTTPHeader();
				input = s.getInputStream();
				replyHeader.readHeader(input);
				addCookies(extractCookies(null, replyHeader), requestHeader);
				
				if ( m_displayHeaders )
				{
					System.out.println("***** Received *****");
					replyHeader.writeHeader(System.out);
				}

			}

			while ( "100".equals(replyHeader.getHeaderAttribute(HTTPHeader.STATUS_CODE)) )
			{
				replyHeader = new HTTPHeader();
				replyHeader.readHeader(input);
				addCookies(extractCookies(null, replyHeader), requestHeader);
			}

			return new MyRETSCallRecord(requestHeader, replyHeader, s);
		}
		catch (Exception e)
		{
			throw new XubException(e.getMessage());
		}
	}

	private Map extractCookies(Map cookies, HTTPHeader header)
	{
		if ( header == null ) return cookies;
		if ( header.getCookieParameterKeys() == null ) return cookies;
		
		if ( cookies == null )
			cookies = new HashMap();
		
		Iterator	keys = header.getCookieParameterKeys().iterator();
		String		key;
		while ( keys.hasNext() )
		{
			key = (String)keys.next();
			cookies.put(key,header.getCookieParameter(key));
		}
		return cookies;
	}
	
	private void addCookies(Map cookies, HTTPHeader header)
	{
		if ( cookies == null ) return;
		
		Iterator	keys = cookies.keySet().iterator();
		Object		key;
		while ( keys.hasNext() )
		{
			key = keys.next();
			header.putCookieParameter((String)key,(String)cookies.get(key));
		}
	}

	private String getUAAuthorizationString()
	{
		if ( m_userAgentPassword != null )
			return "Digest " + hexMD5(hexMD5(m_userAgent+":"+m_userAgentPassword)+":"+m_retsRequestID+":"+m_sessionID+":"+m_retsVersion);
		return null;
	}
	
	private String getBasicAuthorizationString()
	{
		if ( "basic".equalsIgnoreCase(m_authMethod) )
			return "Basic " + base64((m_user+":"+m_password).getBytes());
		return null;
	}
	
	private String getAuthorizationString(String seed, String uri, String method)
	{
		String realm = getAuthorizationParameter(seed, "realm");
		String nonce = getAuthorizationParameter(seed, "nonce");
		String cnonce = CNONCE;
		
//		if ( m_userAgentPassword != null )
//			cnonce=hexMD5(m_userAgent+":"+m_userAgentPassword)+":"+m_retsRequestID+":"+m_nonce);
		if ( m_userAgentPassword != null )
			cnonce=hexMD5(hexMD5(m_userAgent+":"+m_userAgentPassword)+":"+m_retsRequestID+":"+m_sessionID+":"+m_retsVersion);
		
		String auth = "Digest username=\"" + m_user + "\", realm=\"" + realm + "\", nonce=\"" + nonce + "\", uri=\"" + uri + "\"";

		String qop = getAuthorizationParameter(seed, "qop");
		if ( "auth".equals(qop) || "auth-int".equals(qop) )
		{
			if ( ! "RETS/1.0".equals(m_retsVersion) )
			{
				auth += ", qop=\"" + qop +"\"";
				nonce += ":" + NC + ":" + cnonce + ":" + qop;
			}

			auth += ", nc=" + NC + ", cnonce=\"" + cnonce + '"';
			
		}
		else
			auth += ", algorithm=\"MD5\"";
		
		auth += ", response=\"";
		auth += hexMD5(hexMD5(m_user+":"+realm+":"+m_password) + ":"+nonce+":"+ hexMD5(method + ":"+uri));
		auth += '"';

		String opaque = getAuthorizationParameter(seed, "opaque");
		auth += ", opaque=\"" + opaque + "\"";
		return auth;
	}

	private static String getAuthorizationParameter(String seed, String paramName)
	{
		int begin = seed.indexOf(paramName+'=');
		if ( begin < 0 ) return "";
		begin = seed.indexOf('"', begin) + 1;
		int end = seed.indexOf('"', begin);
		return seed.substring(begin, end);
	}
	
	private static final char[] base64Array = {
	'A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P','Q','R',
	'S','T','U','V','W','X','Y','Z','a','b','c','d','e','f','g','h','i','j',
	'k','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z','0','1',
	'2','3','4','5','6','7','8','9','+','/'
	};
	
	private static String base64(byte[] data)
	{
		if ( data == null ) return null;
		int		endOfInput = data.length;
		int		endOfOutput = ((int)((endOfInput + 2)/3) + 1) * 4;
		int		inputChunks = endOfInput/3;
		StringBuffer	output = new StringBuffer(endOfOutput+1);
		
		int	beginChunk;
		int	endChunk;
		int	outputOffset = 0;
		byte	c1, c2, c3;
		for ( int chunk = 0; chunk < inputChunks; ++chunk )
		{
			c1 = data[chunk*3];
			c2 = data[chunk*3+1];
			c3 = data[chunk*3+2];
			
			output.append(base64Array[c1 >> 2]);
			output.append(base64Array[((c1 << 4 ) & 0x30) | (c2 >> 4)]);
			output.append(base64Array[((c2 << 2) & 0x3c) | (c3 >> 6)]);
			output.append(base64Array[c3 & 0x3F]);
		}
		if ( endOfInput-(inputChunks*3) == 1 )
		{
			c1 = data[inputChunks*3];
			output.append(base64Array[c1 >> 2]);
			output.append(base64Array[((c1 << 4 ) & 0x30)]);
			output.append('=');
			output.append('=');
		}
		else if ( endOfInput-(inputChunks*3) == 2 )
		{
			c1 = data[inputChunks*3];
			c2 = data[inputChunks*3+1];
			output.append(base64Array[c1 >> 2]);
			output.append(base64Array[c1 >> 2]);
			output.append(base64Array[((c1 << 4 ) & 0x30) | (c2 >> 4)]);
			output.append(base64Array[((c2 << 2) & 0x3c)]);
			output.append('=');
		}
		
		return output.toString();
	}
	
	private static String hexMD5(String data)
	{
		try
		{
			MessageDigest md = MessageDigest.getInstance("MD5");
			md.reset();
			return encodeHex(md.digest(data.getBytes()));
		}
		catch (Exception e)
		{
			return null;
		}
	}
	
	private static final char hexArray[] = {'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'};

	private static String encodeHex(byte[] data)
	{
		String		hexString = "";
//		char		hexArray[] = {'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'};
		int		index = 0;
		
		while ( index < data.length )
		{
			hexString += hexArray[(data[index] & 0xF0) >> 4];
			hexString += hexArray[data[index] & 0x0F];
			++index;
		}
		
		return hexString;
	}
	
	private static class MyRETSCallRecord
	{
		private HTTPHeader	m_requestHeader;
		private HTTPHeader	m_replyHeader;
		private Socket		m_socket;
		
		public MyRETSCallRecord (HTTPHeader req, HTTPHeader rep, Socket s)
		{
			m_requestHeader = req;
			m_replyHeader = rep;
			m_socket = s;
		}
		
		public Socket getSocket()
		{
			return m_socket;
		}
		
		public void setSocket(Socket s)
		{
			m_socket = s;
		}
		
		public InputStream getPayloadInputStream() throws IOException
		{
			if ( m_socket == null ) throw new IOException("Socket is null.");
			
			InputStream input = null;
			try
			{
				input = m_socket.getInputStream();
			}
			catch ( IOException ioe )
			{
				throw ioe;
			}

			if ( "chunked".equals(getReplyHeader().getHeaderAttribute("Transfer-Encoding")) )
				input = new HTTPChunkedBodyFilter(input);
			else
			{
				String length= getReplyHeader().getHeaderAttribute("Content-Length");
				if ( length != null )
				{
					try
					{
						long payloadLength = Long.parseLong(length);
						input = new ReadLimitFilter(input, payloadLength, false);
					}
					catch ( NumberFormatException nfe )
					{
					}
				}
			}
			return input;
		}
		
		public HTTPHeader getRequestHeader()
		{
			return m_requestHeader;
		}
		
		public void seRequestHeader(HTTPHeader header)
		{
			m_requestHeader = header;
		}
		
		public HTTPHeader getReplyHeader()
		{
			return m_replyHeader;
		}
		
		public void seReplyHeader(HTTPHeader header)
		{
			m_replyHeader = header;
		}
	}
	
	private static class MyMetadataParamExtractor implements IAtomizer
	{
		private String		m_id = "0";
		private String		m_type = "METADATA-RESOURCE";
		private String		m_resource = "";
		
		public MyMetadataParamExtractor()
		{
		}
		
		public String getId()
		{
			if ( m_resource.length() > 0 )
			return m_resource + ":" + m_id;

			return m_id;
		}
		
		public String getType()
		{
			return m_type;
		}
		
		public boolean open(String name, Map attributes)
		{
			return true;
		}
		
		public boolean leaf(String name, Map attributes, Object value)
		{
			if ( value != null )
			{
				if ( "metadata-id".equals(name) )
					m_id = value.toString();
				else if ( "metadata-resource".equals(name) )
					m_resource = value.toString();
				else if ( "metadata-type".equals(name) )
					m_type = value.toString();
			}
			
			return true;
		}
		
		public boolean close(String name)
		{
			return true;
		}
	}
	
	private static class MyLogHandler implements ILogHandler
	{
		private ILogHandler	m_handler;
		private String		m_host;
		private String		m_logToken;
		
		public MyLogHandler(ILogHandler handler, String host)
		{
			m_handler = handler;
			m_host = "Host: " + host;
			m_logToken = String.valueOf(new java.util.Date().getTime());
		}
		
		
		public boolean log(IAtomizable message)
		{
			if ( m_handler == null ) return true;
			return m_handler.log(new StandardLogMessage(new MyMessageWrapper(message, m_logToken), "Rets Client", m_host));
		}
		
		private boolean log(String message)
		{
			if ( m_handler != null )
			{
				message = "<RetsLogMessage>" + message + "</RetsLogMessage>";
				log(new StreamAtomizable(message));
			}
			return true;
		}
		
		private static class MyMessageWrapper implements IAtomizable
		{
			private IAtomizable	m_message;
			private String		m_token;
			
			public MyMessageWrapper(IAtomizable message, String token)
			{
				m_message = message;
				m_token = token;
			}
			
			public boolean write (String name, IAtomizer atomizer)
			{
				if ( atomizer == null ) return true;
				
				if ( name == null ) name = "rets-log-message";
				
				atomizer.open(name, null);
				atomizer.leaf("log-message-token", null, m_token);
				if ( m_message != null )
					m_message.write(null, atomizer);
				atomizer.close(name);
				return true;
			}

			public boolean isReadOnce ()
			{
				if ( m_message == null ) return false;
				return m_message.isReadOnce();
			}
		}
	}
	
	public static void main (String args[])
	{
		Map	initParams = new HashMap();
		initParams.put("host", "demo.crt.realtors.org");
		initParams.put("port", "6103");
		initParams.put("login-uri", "/rets/login");
		initParams.put("search-uri", "/rets/search");
		initParams.put("logout-uri", "/rets/logout");
		initParams.put("user", "Joe");
		initParams.put("password","Schmoe");
		initParams.put("rets-query","(LP=300000-)");
		initParams.put("rets-class", "ResidentialProperty");
		initParams.put("rets-resource", "Property");
		
		RETSClient client = new RETSClient();
		client.init(initParams);
		
		String searchString = "<query>";
		searchString += "</query>";
		
		IAtomizable atomizable = new StreamAtomizable(searchString);
		
		try
		{
//			atomizable = client.respond(atomizable);
//			atomizable.write(null, new com.haplos.xub.Core.StreamAtomizer(System.out));
		}
		catch (Exception e)
		{
			System.out.println("Arrgh!  " + e.getMessage());
		}
		
/***/
		initParams.put("host", "eavantia.com");
		initParams.put("port", "8080");
		initParams.put("login-uri", "/rets/server/login");
		initParams.put("search-uri", "/rets/server/search");
		initParams.put("logout-uri", "/rets/server/logout");
		initParams.put("user", "4390088458");
		initParams.put("password","password");
		initParams.put("rets-query","(LIST_PRICE=1000000+)");
		initParams.put("rets-class", "ResidentialProperty");
		initParams.put("rets-resource", "Property");
		
		client = new RETSClient();
		client.init(initParams);
		
		atomizable = new StreamAtomizable(searchString);
		
		try
		{
//			atomizable = client.respond(atomizable);
//			atomizable.write(null, new com.haplos.xub.Core.StreamAtomizer(System.out));
		}
		catch (Exception e)
		{
			System.out.println("Arrgh!  " + e.getMessage());
		}
/***/

/***/
		initParams.put("host", "cornerstone.mris.com");
		initParams.put("port", "6103");
		initParams.put("login-uri", "/platinum/login");
		initParams.put("search-uri", "/platinum/search");
		initParams.put("logout-uri", "/platinum/logout");
		initParams.put("user", "2011585");
		initParams.put("password","NAR1585");
		initParams.put("rets-query","(ListDate=2005-10-24+)");
		initParams.put("rets-class", "RES");
		initParams.put("rets-resource", "Property");
		initParams.put("rets-limit", "3");
//		initParams.put("rets-output", "ListDate,ListPrice,ListingStatus,PostalCode,ListingID");
		
		client = new RETSClient();
		client.init(initParams);
		
		atomizable = new StreamAtomizable(searchString);
		
		try
		{
			atomizable = client.respond(atomizable);
			atomizable.write(null, new com.haplos.xub.Core.StreamAtomizer(System.out));
		}
		catch (Exception e)
		{
			System.out.println("Arrgh!  " + e.getMessage());
		}
/***/
	}
}

