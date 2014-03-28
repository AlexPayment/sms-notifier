package com.maral.jenkins.notifier;

import com.twilio.sdk.TwilioRestClient;
import com.twilio.sdk.TwilioRestException;
import com.twilio.sdk.resource.factory.MessageFactory;
import com.twilio.sdk.resource.instance.Account;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import net.sf.json.JSONObject;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Jenkins notifier that sends SMS when a build is unsuccessful.
 *
 * @author Alexandre Payment
 * @since 2014-03-26
 */
@SuppressWarnings("UnusedDeclaration")
public class SmsNotifier extends Notifier {

    private static final String PHONE_NUMBER_PATTERN = "\\+(9[976]\\d|8[987530]\\d|6[987]\\d|5[90]\\d|42\\d|3[875]\\d|2[98654321]\\d|9[8543210]|8[6421]|6[6543210]|5[87654321]|4[987654310]|3[9643210]|2[70]|7|1)\\d{1,14}$";

    private final String numbersToNotify;

    @DataBoundConstructor
    @SuppressWarnings("UnusedDeclaration")
    public SmsNotifier(final String numbersToNotify) {
        this.numbersToNotify = numbersToNotify;
    }

    @SuppressWarnings("UnusedDeclaration")
    public FormValidation doCheckNumbersToNotify(@QueryParameter String value) {
        if (validateNumbers(value)) {
            return FormValidation.ok();
        } else {
            return FormValidation.error("The list of numbers to notify cannot be empty and must consist of international formatted phone numbers separated by a space.");
        }
    }

    @Override
    public DescriptionImpl getDescriptor() {
        return (DescriptionImpl) super.getDescriptor();
    }

    @SuppressWarnings("UnusedDeclaration")
    public String getNumbersToNotify() {
        return numbersToNotify;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        if (build.getResult() != Result.SUCCESS) {
            listener.getLogger().println("Notifying unsuccessful build by SMS");
            notify(build, listener);
        }
        return true;
    }

    private String buildBody(final AbstractBuild<?, ?> build) {
        return String.format("Job %s finished with status %s. See details at %s", build.getProject().getDisplayName(), build.getResult(), getDescriptor().getJenkinsUrl() + build.getUrl());
    }

    private void notify(final AbstractBuild<?, ?> build, final BuildListener listener) {
        TwilioRestClient client = new TwilioRestClient(getDescriptor().getTwilioAccountSid(), getDescriptor().getTwilioAuthToken());
        Account account = client.getAccount();
        MessageFactory messageFactory = account.getMessageFactory();
        String body = buildBody(build);
        String[] numbers = numbersToNotify.split(" ");
        for (String number : numbers) {
            List<NameValuePair> params = new ArrayList<NameValuePair>(3);
            params.add(new BasicNameValuePair("To", number));
            params.add(new BasicNameValuePair("From", getDescriptor().getTwilioNumber()));
            params.add(new BasicNameValuePair("Body", body));
            try {
                messageFactory.create(params);
            } catch (final TwilioRestException e) {
                listener.getLogger().println(String.format("Cannot send SMS to %s. Reason: %s, %s", number, e.getErrorCode(), e.getErrorMessage()));
            }
        }
    }

    private boolean validateNumbers(final String value) {
        if (value == null) {
            return false;
        }
        String[] numbers = value.split(" ");
        for (String number : numbers) {
            if (!number.matches(PHONE_NUMBER_PATTERN)) {
                return false;
            }
        }
        return true;
    }

    @Extension
    public static final class DescriptionImpl extends BuildStepDescriptor<Publisher> {

        private String twilioAccountSid;
        private String twilioAuthToken;
        private String twilioNumber;
        private String jenkinsUrl;

        public DescriptionImpl() {
            super(SmsNotifier.class);
            load();
        }

        public String getJenkinsUrl() {
            return jenkinsUrl;
        }

        public String getTwilioAccountSid() {
            return twilioAccountSid;
        }

        public String getTwilioAuthToken() {
            return twilioAuthToken;
        }

        public String getTwilioNumber() {
            return twilioNumber;
        }

        @Override
        public boolean configure(final StaplerRequest req, final JSONObject json) throws FormException {
            twilioAccountSid = json.getString("twilioAccountSid");
            twilioAuthToken = json.getString("twilioAuthToken");
            twilioNumber = json.getString("twilioNumber");
            jenkinsUrl = Jenkins.getInstance().getRootUrl();
            if (jenkinsUrl == null) {
                jenkinsUrl = JenkinsLocationConfiguration.get().getUrl();
            }
            if (jenkinsUrl == null) {
                jenkinsUrl = "http://localhost:8080/";
            }
            save();
            return super.configure(req, json);
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "SMS Notifier";
        }
    }
}
