package nl.nn.adapterframework.senders;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.icegreen.greenmail.junit4.GreenMailRule;
import com.icegreen.greenmail.util.ServerSetupTest;

import jakarta.mail.internet.MimeMessage;
import nl.nn.adapterframework.stream.Message;

public class MailSenderTest2 extends SenderTestBase<MailSender> {

	private final String toAddress="testUser@localhost";
	private final String testUser="testUser";
	private final String testPassword="testPassword";

	@Rule
	public final GreenMailRule greenMail = new GreenMailRule(ServerSetupTest.SMTP);

	@Before
	public void setup() {
		greenMail.setUser(testUser, testPassword);
	}

	@Override
	public MailSender createSender() throws Exception {
		MailSender mailSender = new MailSender();
		mailSender.setSmtpHost("localhost");
		mailSender.setSmtpPort(greenMail.getSmtp().getPort());
		mailSender.setUserId(testUser);
		mailSender.setPassword(testPassword);
		return mailSender;
	}

	@Test
	public void testSendMessageBasic() throws Exception {

		String subject = "My Subject";
		String body = "My Message Goes Here";

		String mailInput = "<email>"
				+ "<recipients>"
				+ "<recipient type=\"to\" name=\"dummy\">" + toAddress + "</recipient>"
				+ "</recipients>"
				+ "<subject>" + subject + "</subject>"
				+ "<from name=\"Me, Myself and I\">me@address.org</from>"
				+ "<message>" + body + "</message>"
			+ "</email>";

		sender.configure();
		sender.open();
		sender.sendMessageOrThrow(new Message(mailInput), session);

		MimeMessage[] messages = greenMail.getReceivedMessages();
		assertEquals(1, messages.length);

		assertEquals(subject, messages[0].getSubject());
		assertEquals(body, messages[0].getContent().toString().trim());
	}

}