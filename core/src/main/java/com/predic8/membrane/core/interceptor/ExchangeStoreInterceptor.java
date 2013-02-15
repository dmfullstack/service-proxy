/* Copyright 2009, 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor;

import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchangestore.ExchangeStore;
import com.predic8.membrane.core.http.AbstractBody;
import com.predic8.membrane.core.http.MessageObserver;
import com.predic8.membrane.core.interceptor.administration.AdminConsoleInterceptor;
import com.predic8.membrane.core.rules.AbstractServiceProxy;
import com.predic8.membrane.core.rules.Rule;

@MCElement(name="exchangeStore")
public class ExchangeStoreInterceptor extends AbstractInterceptor implements ApplicationContextAware {

	private static final String BEAN_ID_ATTRIBUTE_CANNOT_BE_USED = "bean id attribute cannot be used";
	private ApplicationContext applicationContext;
	private static Log log = LogFactory.getLog(AbstractInterceptor.class.getName());
	
	private ExchangeStore store;
	private String exchangeStoreBeanId;
	
	private Set<AbstractServiceProxy> serviceProxiesContainingAdminConsole = new HashSet<AbstractServiceProxy>();
	
	public ExchangeStoreInterceptor() {
		name = "Exchange Store Interceptor";
	}
	
	public ExchangeStoreInterceptor(ExchangeStore exchangeStore) {
		this();
		setExchangeStore(exchangeStore);
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
	
	@Override
	public Outcome handleResponse(final Exchange exc) throws Exception {	
		return handle(exc);
	}
	
	@Override
	public void handleAbort(final Exchange exc) {
		handle(exc);	
	}

	protected Outcome handle(final Exchange exc) {
		if (serviceProxiesContainingAdminConsole.contains(exc.getRule())) {
			return Outcome.CONTINUE;
		}
			
		try {
			exc.getResponse().addObserver(new MessageObserver() {
				public void bodyComplete(AbstractBody body) {
					store.add(exc);
				}
			});
		} catch (Exception e) {
			log.info("Exchange has no probably no response due to a prior error in the response chain.");
		}
		
		return Outcome.CONTINUE;
	}
	
	public ExchangeStore getExchangeStore() {
		return store;		
	}

	@MCAttribute(attributeName="name")
	public void setExchangeStore(ExchangeStore exchangeStore) {
		store = exchangeStore;
		exchangeStoreBeanId = BEAN_ID_ATTRIBUTE_CANNOT_BE_USED;
	}
	
	public String getExchangeStoreBeanId() {
		return exchangeStoreBeanId;
	}
	
	/**
	 * @deprecated use {@link #setExchangeStore(ExchangeStore)} instead: Using
	 *             {@link #setExchangeStoreBeanId(String)} from Spring works,
	 *             but does not create a Spring bean dependency.
	 */
	@Deprecated
	public void setExchangeStoreBeanId(String exchangeStoreBeanId) {
		this.exchangeStoreBeanId = exchangeStoreBeanId;
	}
	
	public void init() throws Exception {
		if (exchangeStoreBeanId == BEAN_ID_ATTRIBUTE_CANNOT_BE_USED)
			; // do nothing as "store" was already set via #setExchangeStore(ExchangeStore)
		else if (exchangeStoreBeanId != null)
			store = applicationContext.getBean(exchangeStoreBeanId, ExchangeStore.class);
		else
			store = router.getExchangeStore();
		
		searchAdminConsole();
	}
	
	private void searchAdminConsole() {
		for (Rule r : router.getRuleManager().getRules()) {
			if (!(r instanceof AbstractServiceProxy)) continue;
			
			for (Interceptor i : r.getInterceptors()) {
				if (i instanceof AdminConsoleInterceptor) {
					serviceProxiesContainingAdminConsole.add((AbstractServiceProxy)r);
				}
			}
		}			
	}
	
	@Override
	public String getShortDescription() {
		return "Logs all exchanges (requests and responses) into an exchange store "+
				"that can be inspected using <a href=\"http://www.membrane-soa.org/soap-monitor/\">Membrane Monitor</a>.";
	}
	
	@Override
	public String getHelpId() {
		return "exchangeStore";
	}

}
