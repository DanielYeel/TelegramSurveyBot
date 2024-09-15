package org.example;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import java.util.*;

public class MyBot extends TelegramLongPollingBot {
    private final String BOT_USERNAME = "UsersSurveysBot";
    private final String BOT_TOKEN = "7154361950:AAE1rvxBxjL8sC9-OB0bDUavst0yA7lxT1k";
    private Set<Long> communityMembers = new HashSet<>();
    private Survey currentSurvey = null;
    private Map<Long, List<String>> surveyCreationData = new HashMap<>();
    private Map<Long, Integer> surveyCreationStep = new HashMap<>();
    private Map<Long, List<Integer>> surveyResponses = new HashMap<>();
    private Timer surveyTimer;


    public MyBot() {}

    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String message = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            if (message.equalsIgnoreCase("hi") || message.equalsIgnoreCase("היי")) {
                handleJoinCommunity(chatId);
            } else if (message.equalsIgnoreCase("/createsurvey")) {
                handleCreateSurveyCommand(chatId);
            } else if (surveyCreationStep.containsKey(chatId)) {
                handleSurveyCreationProcess(chatId, message);
            } else if (currentSurvey != null && !surveyResponses.containsKey(chatId)) {
                handleSurveyResponse(chatId, message);
            } else {
                sendMessage(chatId, "Invalid command or you have already participated in the survey.");
            }
        }
    }

    private void handleJoinCommunity(long chatId) {
        if (communityMembers.add(chatId)) {
            sendMessage(chatId, "You have successfully joined the community!");
            sendMessageToAll("A new member has joined! Total members: " + communityMembers.size());
        } else {
            sendMessage(chatId, "You are already a member of the community.");
        }
    }

    private void handleCreateSurveyCommand(long chatId) {

        if (communityMembers.size() < 3) {
            sendMessage(chatId, "Not enough members to create a survey. You need at least 3 members.");
        } else if (currentSurvey != null) {
            sendMessage(chatId, "There is already an active survey. Please wait until it is finished.");
        } else {
            sendMessage(chatId, "Let's create a new survey! Please provide the survey title.");
            surveyCreationStep.put(chatId, 1);
            surveyCreationData.put(chatId, new ArrayList<>());
        }
    }

    private void createSurvey(long chatId) {
        List<String> data = surveyCreationData.get(chatId);
        String title = data.get(0);
        List<String> questions = new ArrayList<>();
        List<List<String>> options = new ArrayList<>();

        for (int i = 1; i < data.size(); i += 2) {
            questions.add(data.get(i));
            options.add(Arrays.asList(data.get(i + 1).split(",")));
        }

        this.currentSurvey = new Survey(title, questions, options, chatId);
        sendMessage(chatId, "Survey created successfully! Would you like to send it now or schedule it? (send/schedule)");

        surveyCreationStep.put(chatId, 5);
    }


    private void handleSurveyCreationProcess(long chatId, String message) {
        int step = surveyCreationStep.get(chatId);

        switch (step) {
            case 1:
                surveyCreationData.get(chatId).add(message);
                sendMessage(chatId, "Great! Now, please provide the first question.");
                surveyCreationStep.put(chatId, 2);
                break;

            case 2:
                surveyCreationData.get(chatId).add(message);
                sendMessage(chatId, "Please provide the options for this question (comma-separated).");
                surveyCreationStep.put(chatId, 3);
                break;

            case 3:
                List<String> options = Arrays.asList(message.split(","));
                if (options.size() < 2 || options.size() > 4) {
                    sendMessage(chatId, "Please provide 2-4 options separated by commas.");
                } else {
                    surveyCreationData.get(chatId).add(String.join(",", options));
                    int questionCount = (surveyCreationData.get(chatId).size() - 1) / 2;
                    if (questionCount < 3) {
                        sendMessage(chatId, "Do you want to add another question? (yes/no)");
                        surveyCreationStep.put(chatId, 4);
                    } else {
                        sendMessage(chatId, "You've reached the maximum number of 3 questions.");
                        createSurvey(chatId);
                    }
                }
                break;

            case 4:
                if (message.equalsIgnoreCase("yes")) {
                    int questionCount = (surveyCreationData.get(chatId).size() - 1) / 2;
                    if (questionCount < 3) {
                        sendMessage(chatId, "Please provide the next question.");
                        surveyCreationStep.put(chatId, 2);
                    } else {
                        sendMessage(chatId, "You've already added the maximum of 3 questions.");
                        createSurvey(chatId);
                    }
                } else {
                    createSurvey(chatId);
                }
                break;

            case 5:
                if (message.equalsIgnoreCase("send")) {
                    sendSurveyToAllMembers(chatId);
                    surveyCreationStep.remove(chatId);
                    surveyCreationData.remove(chatId);
                } else if (message.equalsIgnoreCase("schedule")) {
                    sendMessage(chatId, "Please provide the delay in minutes for scheduling the survey.");
                    surveyCreationStep.put(chatId, 6); // Move to the scheduling step
                } else {
                    sendMessage(chatId, "Please type 'send' to send the survey now, or 'schedule' to schedule it.");
                }
                break;

            case 6:
                try {
                    int delayMinutes = Integer.parseInt(message);
                    if (delayMinutes <= 0) {
                        sendMessage(chatId, "Invalid number. Please provide the delay in minutes as a positive number.");
                    } else {
                        scheduleSurvey(chatId, delayMinutes);
                        surveyCreationStep.remove(chatId);
                        surveyCreationData.remove(chatId);
                    }
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "Invalid number. Please provide the delay in minutes as a number.");
                }
                break;

            default:
                sendMessage(chatId, "Something went wrong. Please try again.");
                surveyCreationStep.remove(chatId);
                surveyCreationData.remove(chatId);
                break;
        }
    }

    private void handleSurveyResponse(long chatId, String message) {
        if (currentSurvey == null) {
            sendMessage(chatId, "No active survey.");
            return;
        }

        if (chatId == currentSurvey.getCreatorId()) {
            sendMessage(chatId, "You cannot participate in your own survey.");
            return;
        }

        System.out.println("Handling survey response for user: " + chatId);
        System.out.println("Message received: " + message);

        List<Integer> userResponses = new ArrayList<>();
        try {
            String[] responses = message.split("\\s+");
            for (int i = 0; i < responses.length; i++) {
                int optionIndex = Integer.parseInt(responses[i]) - 1;

                if (optionIndex < 0 || optionIndex >= currentSurvey.getOptions().get(i).size()) {
                    sendMessage(chatId, "Invalid response. Please respond with valid option numbers.");
                    return;
                }
                userResponses.add(optionIndex);
            }
        } catch (NumberFormatException e) {
            sendMessage(chatId, "Invalid response format. Please respond with numbers only.");
            return;
        }

        if (userResponses.size() != currentSurvey.getQuestions().size()) {
            sendMessage(chatId, "Please respond with an answer for each question.");
            return;
        }

        surveyResponses.put(chatId, userResponses);
        sendMessage(chatId, "Thank you for your responses!");

        System.out.println("Current survey: " + currentSurvey);
        System.out.println("User responses: " + userResponses);

        if (surveyResponses.size() == communityMembers.size() - 1) {
            sendSurveyResults();
        }
    }


    private void sendSurveyToAllMembers(long initiatorChatId) {
        if (currentSurvey == null) {
            sendMessage(initiatorChatId, "No survey is currently active.");
            return;
        }

        StringBuilder surveyMessage = new StringBuilder();
        surveyMessage.append("Survey: ").append(currentSurvey.getTitle()).append("\n");

        for (int i = 0; i < currentSurvey.getQuestions().size(); i++) {
            surveyMessage.append("\nQuestion ").append(i + 1).append(": ").append(currentSurvey.getQuestions().get(i)).append("\n");
            List<String> options = currentSurvey.getOptions().get(i);
            for (int j = 0; j < options.size(); j++) {
                surveyMessage.append(j + 1).append(". ").append(options.get(j)).append("\n");
            }
        }

        surveyMessage.append("\nPlease answer by typing the number corresponding to your choice, separated by spaces (e.g., '1 2'). ");
        surveyMessage.append("For Yes/No questions, you can also type 'Yes' or 'No'.");

        sendMessage(initiatorChatId, "Sending the survey to all community members...");

        for (Long memberId : communityMembers) {
            if (!memberId.equals(initiatorChatId)) {
                sendMessage(memberId, surveyMessage.toString());
            }
        }

        sendMessage(initiatorChatId, "Survey sent successfully!");
        surveyTimer = new Timer();
        surveyTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                sendSurveyResults();
            }
        }, 5 * 60 * 1000);
    }

    private void scheduleSurvey(long chatId, int delayMinutes) {
        sendMessage(chatId, "Survey scheduled successfully. It will be sent in " + delayMinutes + " minutes.");

        surveyTimer = new Timer();
        surveyTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                sendSurveyToAllMembers(chatId);
            }
        }, delayMinutes * 60 * 1000);
    }

    private void sendSurveyResults() {

        if (surveyResponses.isEmpty()) {
            sendMessageToAll("No responses were collected for the survey.");
            return;
        }

        StringBuilder resultsMessage = new StringBuilder();
        resultsMessage.append("Survey Results: ").append(currentSurvey.getTitle()).append("\n");

        int totalResponses = surveyResponses.size();

        for (int i = 0; i < currentSurvey.getQuestions().size(); i++) {
            resultsMessage.append("\nQuestion ").append(i + 1).append(": ").append(currentSurvey.getQuestions().get(i)).append("\n");
            Map<Integer, Integer> optionCounts = new HashMap<>();

            for (List<Integer> responses : surveyResponses.values()) {
                int response = responses.get(i);
                optionCounts.put(response, optionCounts.getOrDefault(response, 0) + 1);
            }

            List<String> options = currentSurvey.getOptions().get(i);
            List<Map.Entry<Integer, Double>> sortedOptions = new ArrayList<>();

            for (int j = 0; j < options.size(); j++) {
                int count = optionCounts.getOrDefault(j, 0);
                double percentage = (count / (double) totalResponses) * 100;
                sortedOptions.add(new AbstractMap.SimpleEntry<>(j, percentage));
            }

            sortedOptions.sort((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()));

            for (Map.Entry<Integer, Double> entry : sortedOptions) {
                int optionIndex = entry.getKey();
                double percentage = entry.getValue();
                resultsMessage.append(optionIndex + 1).append(". ").append(options.get(optionIndex)).append(" - ")
                        .append(optionCounts.getOrDefault(optionIndex, 0)).append(" votes (")
                        .append(String.format("%.2f", percentage)).append("%)").append("\n");
            }
        }

        sendMessageToAll(resultsMessage.toString());

        currentSurvey = null;
        surveyResponses.clear();
    }

    private void sendMessageToAll(String text) {
        for (Long memberId : communityMembers) {
            sendMessage(memberId, text);
        }
    }

    private void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public String getBotToken() {
        return BOT_TOKEN;
    }

    public String getBotUsername() {
        return BOT_USERNAME;
    }
}
