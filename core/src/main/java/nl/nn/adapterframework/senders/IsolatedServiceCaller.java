/*
   Copyright 2013, 2020 Nationale-Nederlanden, 2022-2023 WeAreFrank!

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package nl.nn.adapterframework.senders;

import org.apache.logging.log4j.Logger;
import org.springframework.core.task.TaskExecutor;

import nl.nn.adapterframework.core.ListenerException;
import nl.nn.adapterframework.core.PipeLineSession;
import nl.nn.adapterframework.core.SenderResult;
import nl.nn.adapterframework.receivers.ServiceClient;
import nl.nn.adapterframework.stream.Message;
import nl.nn.adapterframework.stream.ThreadLifeCycleEventListener;
import nl.nn.adapterframework.util.ClassUtils;
import nl.nn.adapterframework.util.Guard;
import nl.nn.adapterframework.util.LogUtil;

/**
 * Helper class for {@link IbisLocalSender} that wraps around {@link ServiceClient} implementation to make calls to a local Ibis adapter in a separate thread.
 *
 * @author  Gerrit van Brakel
 * @since   4.3
 */
public class IsolatedServiceCaller {
	protected Logger log = LogUtil.getLogger(this);

	/**
	 * The thread-pool for spawning threads, injected by Spring
	 */
	private TaskExecutor taskExecutor;

	public void setTaskExecutor(TaskExecutor executor) {
		taskExecutor = executor;
	}

	public TaskExecutor getTaskExecutor() {
		return taskExecutor;
	}

	public void callServiceAsynchronous(ServiceClient service, Message message, PipeLineSession session, ThreadLifeCycleEventListener threadLifeCycleEventListener) {
		IsolatedServiceExecutor ise=new IsolatedServiceExecutor(service, message, session, null, threadLifeCycleEventListener);
		getTaskExecutor().execute(ise);
	}

	public SenderResult callServiceIsolated(ServiceClient service, Message message, PipeLineSession session, ThreadLifeCycleEventListener threadLifeCycleEventListener) throws ListenerException {
		Guard guard = new Guard();
		guard.addResource();
		IsolatedServiceExecutor ise=new IsolatedServiceExecutor(service, message, session, guard, threadLifeCycleEventListener);
		getTaskExecutor().execute(ise);
		try {
			guard.waitForAllResources();
		} catch (InterruptedException e) {
			throw new ListenerException(ClassUtils.nameOf(this)+" was interrupted",e);
		}
		if (ise.getThrowable() != null) {
			if (ise.getThrowable() instanceof ListenerException) {
				throw (ListenerException)ise.getThrowable();
			}
			throw new ListenerException(ise.getThrowable());
		}
		return ise.getReply();
	}
}