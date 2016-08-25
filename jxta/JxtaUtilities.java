package com.haplos.xub.plugins.jxta;

import com.haplos.xub.Toolkit.ComponentFactory;

import net.jxta.peergroup.PeerGroup;
import net.jxta.peergroup.PeerGroupFactory;
import net.jxta.exception.PeerGroupException;
import net.jxta.document.AdvertisementFactory;
import net.jxta.document.MimeMediaType;
import net.jxta.socket.JxtaServerSocket;
import net.jxta.protocol.PipeAdvertisement;
import net.jxta.document.Advertisement;
import net.jxta.document.StructuredTextDocument;
import net.jxta.document.MimeMediaType ;
import net.jxta.discovery.DiscoveryService;
import net.jxta.pipe.PipeService;
import net.jxta.id.IDFactory;
import net.jxta.ext.config.Configurator;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Enumeration;

/*
 * by Dave Scott, 2005
 *
 * Copyright and license conditions are contained in the file named
 * 'LICENSE.TXT' which is distributed with this source code.  Online
 * copies of the license conditions can be found at
 *         http://haplos.com/licences/license.html
 */

/**
 * Class full of utility methods for creating JXTA objects, primarily derived from the JXTA Programmer's guide.
 * 
 * @author Dave Scott
 */
 
public class JxtaUtilities
{
	private static PeerGroup	m_netPeerGroup = null;
	private static Map		m_peerGroups = null;
	private static String		m_cacheDirectory = "jcache/";
	public static final String	kIsRendezvous = "rendezvous";
	
	//  Pipe utilities
	public static PipeAdvertisement getPipeAdvertisement(Map parameters)
	{
		PeerGroup group = getGroup(parameters);
		String id = ComponentFactory.getParameter("service-id", null, parameters);
		Advertisement advertisement = loadAdvertisement(group, id, null);

		if ( advertisement == null )
		{
			String pipeName = ComponentFactory.getParameter("service-id", null, parameters);
			advertisement = makePipeAdvertisement(group, pipeName);
			saveAdvertisement(group, id, advertisement, null);
		}
		else if ( ! (advertisement instanceof PipeAdvertisement) )
			return null;
		
		return (PipeAdvertisement)advertisement;
	}
	
	public static PipeAdvertisement findPipeAdvertisement(Map parameters)
	{
		PeerGroup group = getGroup(parameters);
		String id = ComponentFactory.getParameter("service-id", null, parameters);
		Advertisement advertisement = loadAdvertisement(group, id, null);

		if ( advertisement == null )
			advertisement = discoverAdvertisement(group, DiscoveryService.ADV, ComponentFactory.getParameter("service-id", null, parameters), 30, 5);
		
		if ( advertisement != null )
			saveAdvertisement(group, id, advertisement, null);
		else if ( ! (advertisement instanceof PipeAdvertisement) )
			return null;
		
		return (PipeAdvertisement)advertisement;
	}
	
	public static PipeAdvertisement makePipeAdvertisement(PeerGroup group, String id)
	{
		if ( group == null ) return null;
		PipeAdvertisement advertisement = (PipeAdvertisement)AdvertisementFactory.newAdvertisement(PipeAdvertisement.getAdvertisementType());
		advertisement.setPipeID(IDFactory.newPipeID(group.getPeerGroupID()));
		advertisement.setType(PipeService.UnicastType);
		advertisement.setDescription("ServiceType: " + id);
		advertisement.setName(id);
	
		try
		{
			DiscoveryService discovery = group.getDiscoveryService(); 
			discovery.publish( advertisement); 
			discovery.remotePublish(advertisement); 
		}
		catch (java.io.IOException ioe)
		{
		}
	
		return advertisement;
	}
	
	// Group Utilities
	public static PeerGroup getGroup(Map parameters)
	{
		if ( ComponentFactory.getBooleanParameter(kIsRendezvous, false, parameters) )
			startGroupRendezvous(ComponentFactory.getParameter(ComponentFactory.GROUP, null, parameters));
		
		return getGroup(ComponentFactory.getParameter(ComponentFactory.GROUP, null, parameters));
	}
	
	public static PeerGroup getGroup(String groupName)
	{
		startJxta();
		if ( groupName == null ) return m_netPeerGroup;

		if ( m_peerGroups == null )
			m_peerGroups = new HashMap();

		// First check existing PeerGroup instances
		PeerGroup group = (PeerGroup)m_peerGroups.get(groupName);
		if ( group != null ) return group;
		
		// Next, check cache group advertisements
		Advertisement advertisement = loadAdvertisement(m_netPeerGroup, groupName, null);
		if ( advertisement != null )
		{
			try
			{
				group = m_netPeerGroup.newGroup(advertisement);
			}
			catch (net.jxta.exception.PeerGroupException pge)
			{
			}
		}
		
		if ( group == null )
		{
			// Finally, attempt to discover group
			advertisement = discoverPeerGroupAdvertisement(groupName);
			if ( advertisement == null ) return null;
			try
			{
				group = m_netPeerGroup.newGroup(advertisement);
				saveAdvertisement(m_netPeerGroup, group.getPeerGroupName(), group.getPeerGroupAdvertisement(), null);
			}
			catch (net.jxta.exception.PeerGroupException pge)
			{
			}
		}
		
		// If found, add to instance map
		if ( group != null )
			m_peerGroups.put(groupName, group);

		return group;
	}
	
	public static Advertisement discoverPeerGroupAdvertisement(String groupName)
	{
		startJxta();
		return discoverAdvertisement(m_netPeerGroup, DiscoveryService.GROUP, groupName, 60, 5);
	}

	public static PeerGroup makePeerGroup(String groupName)
	{
		startJxta();
		if ( m_peerGroups == null )
			m_peerGroups = new HashMap();

		// First check existing PeerGroup instances
		PeerGroup group = (PeerGroup)m_peerGroups.get(groupName);
		if ( group != null ) return group;
		
		try
		{
			Advertisement adv = m_netPeerGroup.getAllPurposePeerGroupImplAdvertisement();
			group = m_netPeerGroup.newGroup (IDFactory.newPeerGroupID(), adv, groupName, groupName);
			m_peerGroups.put(groupName, group);
			saveAdvertisement(m_netPeerGroup, group.getPeerGroupName(), group.getPeerGroupAdvertisement(), null);
			return group;
		}
		catch (Exception e)
		{
		}
		
		return group;
	}
	
	public static PeerGroup startGroupRendezvous(String groupName)
	{
		PeerGroup group = getGroup(groupName);
		if ( group == null )
			group = makePeerGroup(groupName);
		if ( group == null ) return null;
		group.getRendezVousService().startRendezVous();
		return group;
	}
	
	public static PeerGroup startGroupRendezvous(Map parameters)
	{
		PeerGroup group = getGroup(parameters);
		return group;
	}
	
	public static boolean startRelay()
	{
//		Configurator jxtaConfig = new Configurator(platformConfig);
//		jxtaConfig.setRelay(true);
		return false;
	}
	
	//  Advertisement Utilities
	public static Advertisement loadAdvertisement(PeerGroup group, String id, InputStream input)
	{
System.out.println("loadAdvertisement: " + id);
		if ( id == null ) return null;
		
		Advertisement advertisement = null;
		try
		{
			if ( input == null )
				input = new FileInputStream(getAdvertisementLocation(group, id));
			advertisement = AdvertisementFactory.newAdvertisement(MimeMediaType.XMLUTF8, input);
//			TextElement advDoc = (TextElement)StructuredDocumentFactory.newStructuredDocument(MimeMediaType.XMLUTF8, input);
//			advertisement = AdvertisementFactory.newAdvertisement(advDoc);
//			
			input.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return advertisement;
	}
	
	public static void saveAdvertisement(PeerGroup group, String id, Advertisement advertisement, OutputStream output)
	{
System.out.println("saveAdvertisement: " + id);
		try
		{
			StructuredTextDocument std = (StructuredTextDocument)advertisement.getDocument(new MimeMediaType("text/xml"));
			if ( output == null )
			{
				String location = getAdvertisementLocation(group, id);
System.out.println("saveAdvertisement: location: " + location);
				java.io.File desc = new java.io.File(location);
				desc.getParentFile().mkdirs();
				desc.createNewFile();
				output = new FileOutputStream (desc);
			}
			std.sendToStream(output);
			output.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void setCacheDirectory(String cacheDirectory)
	{
		m_cacheDirectory = cacheDirectory;
		if ( m_cacheDirectory == null )
			m_cacheDirectory = "jcache/";
		else if ( m_cacheDirectory.charAt(m_cacheDirectory.length()-1) != '/' )
			m_cacheDirectory += "/";
	}
	
	private static String getAdvertisementLocation(PeerGroup group, String id)
	{
		return m_cacheDirectory + group.getPeerGroupName() + "/" + id + ".xml";
	}
	
	private static Advertisement discoverAdvertisement(PeerGroup group, int type, String name, int timeOut, int checkInterval)
	{
System.out.println("discoverAdvertisement: " + name);
		if ( group == null ) return null;
		DiscoveryService discoveryService = group.getDiscoveryService();
		
		Enumeration advertisements = null;
		
		try
		{
			do
			{
				advertisements = discoveryService.getLocalAdvertisements(type, "Name", name);
				if ( (advertisements != null) && (advertisements.hasMoreElements()) )
					return (Advertisement)advertisements.nextElement();
	
				discoveryService.getRemoteAdvertisements(null, type, null, null, 20);
				try
				{
					Thread.sleep(checkInterval*1000);
				}
				catch (InterruptedException ie)
				{
				}
				timeOut -= checkInterval;
			} while (timeOut > 0);
			
			advertisements = discoveryService.getLocalAdvertisements(type, "Name", name);
			if ( (advertisements != null) && (advertisements.hasMoreElements()) )
				return (Advertisement)advertisements.nextElement();
		}
		catch (java.io.IOException ioe)
		{
		}
		
		return null;
	}

	public static Enumeration discoverAdvertisements(PeerGroup group, int type, String name, int timeOut)
	{
		if ( group == null ) return null;
		DiscoveryService discoveryService = group.getDiscoveryService();
		discoveryService.getRemoteAdvertisements(null, type, null, null, timeOut*3);
		try
		{
			Thread.sleep(timeOut*1000);
		}
		catch (InterruptedException ie)
		{
		}
		
		try
		{
			Enumeration advertisements = discoveryService.getLocalAdvertisements(type, (name==null ? null : "Name"), name);
			return advertisements;
		}
		catch ( java.io.IOException ioe)
		{
		}
		
		return null;
	}

	public static void tickleAdvertisements(PeerGroup group)
	{
		if ( group == null ) return;
		DiscoveryService discoveryService = group.getDiscoveryService();
		discoveryService.getRemoteAdvertisements(null, DiscoveryService.ADV, null, null, 20);
	}
	
	public static Enumeration getAdvertisements(PeerGroup group, String name)
	{
		if ( group == null ) return null;
		tickleAdvertisements(group);  //  Won't affect this enumeration, but forces a tickle for next call
		DiscoveryService discoveryService = group.getDiscoveryService();
		try
		{
			Enumeration advertisements = discoveryService.getLocalAdvertisements(DiscoveryService.ADV, "Name", name);
			return advertisements;
		}
		catch (java.io.IOException ioe)
		{
		}
		return null;
	}
	
	public static Enumeration getAdvertisements(PeerGroup group)
	{
		return getAdvertisements(group, null);
	}
	
	public static void startJxta()
	{
		try
		{
			if ( m_netPeerGroup == null )
				m_netPeerGroup = PeerGroupFactory.newNetPeerGroup();
		}
		catch ( PeerGroupException pge )
		{
			pge.printStackTrace();
		}
	}
}

