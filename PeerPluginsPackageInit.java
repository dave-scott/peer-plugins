package com.haplos.xub.plugins;


import com.haplos.xub.Toolkit.ComponentFactory;
import com.haplos.xub.Connectable.IInitable;

import java.util.Map;

/*
 * by Dave Scott, 2005
 *
 * Copyright and license conditions are contained in the file named
 * 'LICENSE.TXT' which is distributed with this source code.  Online
 * copies of the license conditions can be found at
 *         http://haplos.com/licences/license.html
 */

public class PeerPluginsPackageInit implements IInitable
{
        public PeerPluginsPackageInit()
        {
        }

        public boolean init(Map parameters)
        {
		ComponentFactory.registerComponent("jxta", ComponentFactory.SERVICE, "com.haplos.xub.plugins.jxta.JxtaService");
		ComponentFactory.registerComponent("jxta", ComponentFactory.RESPONDER, "com.haplos.xub.plugins.jxta.JxtaRequestHandler");
		ComponentFactory.registerComponent("jxta", ComponentFactory.REQUESTER, "com.haplos.xub.plugins.jxta.JxtaRequester");
		ComponentFactory.registerComponent("jxta", ComponentFactory.RECEIVER, "com.haplos.xub.plugins.jxta.JxtaReceiver");
		ComponentFactory.registerComponent("jxta", ComponentFactory.SENDER, "com.haplos.xub.plugins.jxta.JxtaSender");
		ComponentFactory.registerComponent("jxta-sync", ComponentFactory.RESPONDER, "com.haplos.xub.plugins.jxta.JxtaResponder");
		ComponentFactory.registerComponent("async-jxta", ComponentFactory.SENDER, "com.haplos.xub.plugins.jxta.AsyncJxtaSender");
		ComponentFactory.registerComponent("async-jxta", ComponentFactory.RECEIVER, "com.haplos.xub.plugins.jxta.AsyncJxtaBroadcaster");

		ComponentFactory.registerComponent("rets", ComponentFactory.FILTER, "com.haplos.xub.plugins.rets.RETSAtomizerFilter");
		ComponentFactory.registerComponent("rets", ComponentFactory.RESPONDER, "com.haplos.xub.plugins.rets.RETSResponder");
		ComponentFactory.registerComponent("rets-single", ComponentFactory.RESPONDER, "com.haplos.xub.plugins.rets.RETSClient");
		return true;
	}
}

