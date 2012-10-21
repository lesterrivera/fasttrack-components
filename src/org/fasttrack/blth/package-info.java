/**
 * Copyright (c) 2010 Lester Rivera
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
/**
 * Contains the Business Logic Task Handler (BLTH) classes. In CA Identity Manager, The Business Logic Task 
 * Handler (BLTH) lets you perform custom business logic during data validation operations for a specified 
 * task. During task execution, business logic task handlers have access to the information in the current 
 * task session and can execute the business logic at various points.
 * 
 * In addition, a Business Logic Task Handler (BLTH) can define a custom informational message, such as an 
 * acknowledgement, to display to the user after the user successfully submits a task. If validation errors 
 * occur after a user submits a task screen, the task screen can be redisplayed with the appropriate exception 
 * messages. This process gives the user the opportunity to correct the errors and resubmit the task.
 * 
 * @author Lester Rivera
 * @see <a href="https://supportcontent.ca.com/cadocs/0/CA%20Identity%20Manager%20r12%205%20SP3-ENU/Bookshelf_Files/HTML/javadoc-im/index.htm?toc.htm?887982.html">Programming Guide for Java (for CA Identity Manager 12.5 SP3)</a>
 * @see <a href="https://supportcontent.ca.com/cadocs/0/CA%20Identity%20Manager%20r12%205%20SP3-ENU/Bookshelf_Files/HTML/javadoc-im/index.htm?toc.htm?Business_Logic_Task_Handler_API.html">Business Logic Task Handler API (for CA Identity Manager 12.5 SP3)</a>
 */
package org.fasttrack.blth;