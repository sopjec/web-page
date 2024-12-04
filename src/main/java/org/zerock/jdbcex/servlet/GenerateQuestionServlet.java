package org.zerock.jdbcex.servlet;

import org.zerock.jdbcex.dto.UserDTO;
import org.zerock.jdbcex.util.ConnectionUtil;

import org.json.JSONObject;
import org.json.JSONArray;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@WebServlet("/api/generate-question")
public class GenerateQuestionServlet extends HttpServlet {

    private static final String GPT_API_KEY = System.getenv("GPT_API_KEY");

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("loggedInUser") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        UserDTO loggedInUser = (UserDTO) session.getAttribute("loggedInUser");
        String userId = loggedInUser.getId();

        String resumeId = request.getParameter("resumeId");
        if (resumeId == null || resumeId.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        try (Connection conn = ConnectionUtil.INSTANCE.getConnection()) {
            System.out.println("Database connection established.");

            // Get resume content
            StringBuilder resumeContent = getResumeContent(resumeId, conn);
            if (resumeContent.length() == 0) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            // Generate questions
            String prompt = "Generate questions based on the resume content.";
            String rawResponse = callOpenAI(prompt);
            List<String> questions = ensureMinimumQuestions(rawResponse, prompt);

            // Save interview data
            String resumeTitle = getResumeTitle(resumeId, conn);
            if (resumeTitle == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            int interviewId = saveInterviewData(userId, resumeTitle, conn);

            // Send JSON response
            JSONObject jsonResponse = new JSONObject();
            jsonResponse.put("questions", new JSONArray(questions));
            jsonResponse.put("interviewId", interviewId);

            response.getWriter().write(jsonResponse.toString());
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private String callOpenAI(String prompt) throws Exception {
        String apiUrl = "https://api.openai.com/v1/chat/completions";
        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + GPT_API_KEY);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        JSONObject requestBody = new JSONObject();
        requestBody.put("model", "gpt-3.5-turbo");

        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", "너는 한국어로 답변하는 면접 질문 생성 AI이다."));
        messages.put(new JSONObject().put("role", "user").put("content", prompt));
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", 1000);
        requestBody.put("temperature", 0.7);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(requestBody.toString().getBytes("UTF-8"));
            os.flush();
        }

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("OpenAI API 호출 실패");
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
            StringBuilder responseString = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                responseString.append(line.trim());
            }

            JSONObject jsonResponse = new JSONObject(responseString.toString());
            return jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
        }
    }

    //자소서 아이디 불러오기
    private StringBuilder getResumeContent(String resumeId, Connection conn) throws SQLException {
        StringBuilder resumeContent = new StringBuilder();
        String query = "SELECT question, answer FROM resume_qna WHERE resume_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, Integer.parseInt(resumeId));
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    resumeContent.append("Q: ").append(rs.getString("question"))
                            .append("\nA: ").append(rs.getString("answer"))
                            .append("\n\n");
                }
            }
        }
        return resumeContent;
    }

    //최소 질문 개수
    private List<String> ensureMinimumQuestions(String rawResponse, String prompt) throws Exception {
        List<String> questions = Arrays.stream(rawResponse.split("\n"))
                .filter(q -> q.trim().matches("^\\d+\\.\\s+.*")) // 번호가 있는 질문 필터링
                .map(q -> q.replaceFirst("^\\d+\\.\\s+", "")) // 번호 제거
                .collect(Collectors.toList());

        int maxRetries = 3; // 최대 추가 호출 횟수
        int retries = 0;

        while (questions.size() < 5 && retries < maxRetries) {
            retries++;
            String additionalPrompt = "현재 질문은 " + questions.size() + "개입니다. 추가로 " + (5 - questions.size())
                    + "개의 질문을 생성해 주세요. 질문은 번호를 매겨 출력하세요.";
            String additionalResponse = callOpenAI(prompt + "\n" + additionalPrompt);

            List<String> additionalQuestions = Arrays.stream(additionalResponse.split("\n"))
                    .filter(q -> q.trim().matches("^\\d+\\.\\s+.*"))
                    .map(q -> q.replaceFirst("^\\d+\\.\\s+", ""))
                    .collect(Collectors.toList());

            for (String question : additionalQuestions) {
                if (!questions.contains(question)) {
                    questions.add(question);
                }
            }
        }

        while (questions.size() < 5) {
            questions.add("기본 질문 " + (questions.size() + 1));
        }

        return questions;
    }

    //인터뷰 데이터 저장, 인터뷰 id 불러오기
    private int saveInterviewData(String userId, String resumeTitle, Connection conn) throws Exception {
        int interviewId = -1;
        String interviewDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
        String insertSQL = "INSERT INTO interview (user_id, title, interview_date) VALUES (?, ?, ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(insertSQL, PreparedStatement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, resumeTitle);
            pstmt.setString(3, interviewDate);
            pstmt.executeUpdate();

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    interviewId = generatedKeys.getInt(1);
                    System.out.println("Generated Interview ID: " + interviewId);
                }
            }
        }
        return interviewId;
    }

    // 비디오 저장 경로 설정 및 파일 생성
    private String saveVideoPath(HttpServletRequest request, String finalTitle) {
        // 웹 애플리케이션의 상대 경로 기준으로 설정
        String relativePath = "/videos/" + finalTitle + ".webm";

        // 애플리케이션 루트 경로 가져오기
        String absolutePath = request.getServletContext().getRealPath(relativePath);

        File file = new File(absolutePath);
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs(); // 디렉토리 생성
            }
            // 기존 파일명이 있다면 무조건 덮어씁니다.
            if (!file.exists()) {
                file.createNewFile(); // 파일 생성
            }
            System.out.println("File successfully created: " + file.getPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return relativePath; // 상대 경로 반환
    }



    //자소서 제목 불러오기
    private String getResumeTitle(String resumeId, Connection conn) throws SQLException {
        String query = "SELECT title FROM resume WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, Integer.parseInt(resumeId));
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("title");
                }
            }
        }
        return null;
    }

    // 인터뷰 제목 업데이트 메서드
    private void updateInterviewTitle(int interviewId, String finalTitle, Connection conn) throws SQLException {
        String updateSQL = "UPDATE interview SET title = ? WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(updateSQL)) {
            pstmt.setString(1, finalTitle);
            pstmt.setInt(2, interviewId);
            pstmt.executeUpdate();
        }
    }
}