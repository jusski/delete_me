import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(String[] args) throws Exception {
        // Task name as branch name
        String branch = executeCommandLine("git rev-parse --abbrev-ref HEAD");

        // Jenkins ticket
        String command =
                "curl -s -u jskierus:6jASISAWpObUGZqAQNQf " +
                "-X GET -H \"Content-Type: application/json\" " +
                "https://jira.freshdirect.com/rest/api/latest/issue/%s | " +
                "jq \".fields.summary\"";
        String summary = executeCommandLine(String.format(command, branch));
        summary = summary.replaceAll(" [Automation] ", "").replaceAll("'", "\\'");
        summary = executeCommandLine(String.format("echo | fzf --query '%s' --print-query", summary));

        // Github labels
        List<String> labels = executeCommandLineMultiline("printf \"web\\nregression\\nsmoke\" | fzf -m --bind=space:toggle");

        String labelsArgument = "";
        for (String label : labels) {
            labelsArgument += String.format("--label '%s'", label);
        }

        // Open pull request
        command = "gh pr create --body 'Results: ' --title '%s: %s' --draft --base develop %s";
        List<String> result = executeCommandLineMultiline(String.format(command, branch, summary, labelsArgument));
        result.forEach(System.out::println);

        System.out.println("End");
    }

    private static List<String> executeCommandLineMultiline(String command) throws IOException, InterruptedException {
        List<String> result = new ArrayList<>();

        Process process = new ProcessBuilder("cmd", "/c", command)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .redirectInput(ProcessBuilder.Redirect.INHERIT)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start();
        InputStream inputStream = process.getInputStream();

        if (process.waitFor(10, TimeUnit.SECONDS)) {
            if (process.exitValue() == 0) {
                String line = null;
                List<String> lines = new ArrayList<>();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

                while ((line = bufferedReader.readLine()) != null) {
                    lines.add(line);
                }

                result = lines;
            } else {
                System.err.println("ERROR: executing command '" + command + "'.");
                System.exit(process.exitValue());
            }
        } else {
            System.err.println("ERROR: Timeout while executing: '" + command + "'.");
            System.exit(1);
        }

        return result;
    }

    private static String executeCommandLine(String command) throws IOException, InterruptedException {
        List<String> lines = executeCommandLineMultiline(command);
        if (lines.size() > 1 || lines.size() == 0) {
            lines.forEach(System.err::println);
            System.exit(1);
        }

        return lines.get(0);
    }
}
