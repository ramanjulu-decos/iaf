/*
   Copyright 2019 Nationale-Nederlanden

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
package nl.nn.adapterframework.scheduler.job;

import lombok.Getter;
import lombok.Setter;
import nl.nn.adapterframework.configuration.ConfigurationException;
import nl.nn.adapterframework.scheduler.JobDefFunctions;

public class DatabaseJob extends SendMessageJob {
	private @Getter @Setter String adapterName; //Allow for easily selecting a JavaListener in the console

	@Override
	public void configure() throws ConfigurationException {
		setFunction(JobDefFunctions.SEND_MESSAGE.getLabel());
		super.configure();
	}
}