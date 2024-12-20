package org.zerock.jdbcex.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.zerock.jdbcex.dao.CommentDAO;
import org.zerock.jdbcex.dao.ReviewDAO;
import org.zerock.jdbcex.dto.CommentDTO;
import org.zerock.jdbcex.dto.ReviewDTO;
import org.zerock.jdbcex.dto.UserDTO;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@WebServlet("/reviewDetail")
public class ReviewDetailController extends HttpServlet {
    private final ReviewDAO reviewDAO = new ReviewDAO();
    private final CommentDAO commentDAO = new CommentDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("text/html; charset=UTF-8");
        request.setCharacterEncoding("UTF-8");

        try {
            String action = request.getParameter("action");

            if (action == null || action.isEmpty()) {
                // 기본 상세보기 요청
                handleDetailView(request, response);
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("잘못된 요청입니다.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "요청 처리 중 오류 발생");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        request.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);

        UserDTO loggedInUser = (UserDTO) session.getAttribute("loggedInUser");

        // 액션 파라미터 읽기
        String action = request.getParameter("action");
        int reviewId = Integer.parseInt(request.getParameter("reviewId"));


        // 로그 출력
        System.out.println("reviewId: " + reviewId);
        System.out.println("action: " + action);

        try {

            switch (action) {
                case "like":
                    if (session == null || session.getAttribute("loggedInUser") == null) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.getWriter().write("{\"error\": \"로그인이 필요합니다.\"}");

                        response.sendRedirect("login.jsp");
                        return;
                    }
                    handleLikeAction(loggedInUser.getId(), reviewId, response);
                    response.sendRedirect("reviewDetail?review_id=" + reviewId);
                    break;

                case "unlike":
                    if (session == null || session.getAttribute("loggedInUser") == null) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.getWriter().write("{\"error\": \"로그인이 필요합니다.\"}");

                        response.sendRedirect("login.jsp");
                        return;
                    }
                    handleUnlikeAction(loggedInUser.getId(), reviewId, response);
                    response.sendRedirect("reviewDetail?review_id=" + reviewId);
                    break;


                case "addComment":
                    // 댓글 추가
                    if (session == null || session.getAttribute("loggedInUser") == null) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.getWriter().write("{\"error\": \"로그인이 필요합니다.\"}");

                        response.sendRedirect("login.jsp");
                        return;
                    }
                    System.out.println("addComment옴");
                    handleAddCommentAction(reviewId, request, response);
                    break;

                default:
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.getWriter().write("{\"error\": \"잘못된 요청입니다.\"}");
            }
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\": \"유효하지 않은 리뷰 ID입니다.\"}");
        } catch (Exception e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "요청 처리 중 오류 발생");
        }
    }

    private void handleDetailView(HttpServletRequest request, HttpServletResponse response) throws Exception {
        int reviewId = Integer.parseInt(request.getParameter("review_id"));

        // 리뷰 상세정보 가져오기
        ReviewDTO review = reviewDAO.getReviewById(reviewId);

        // 댓글 리스트 가져오기
        List<CommentDTO> comments = commentDAO.getCommentsByReviewId(reviewId);

        // 공감 여부 확인
        HttpSession session = request.getSession(false);
        boolean isLikedByUser = false;
        if (session != null) {
            UserDTO loggedInUser = (UserDTO) session.getAttribute("loggedInUser");
            if (loggedInUser != null) {
                isLikedByUser = reviewDAO.isLikedByUser(loggedInUser.getId(), reviewId);
            }
        }

        // JSP에 전달
        request.setAttribute("review", review);
        request.setAttribute("comments", comments);
        request.setAttribute("likedByUser", isLikedByUser);

        request.getRequestDispatcher("/review_detail.jsp").forward(request, response);
    }

    private void handleLikeAction(String userId, int reviewId, HttpServletResponse response) throws Exception {
        response.setContentType("application/json;charset=UTF-8"); // JSON 응답 설정
        System.out.println("reviewId : " + reviewId);
        reviewDAO.likeReview(userId, reviewId);
        int updatedLikes = reviewDAO.getLikes(reviewId);
        response.getWriter().write("{\"likes\": " + updatedLikes + "}");
    }

    private void handleUnlikeAction(String userId, int reviewId, HttpServletResponse response) throws Exception {
        reviewDAO.unlikeReview(userId, reviewId);
        int updatedLikes = reviewDAO.getLikes(reviewId);
        response.getWriter().write("{\"likes\": " + updatedLikes + "}");
    }

    private void handleAddCommentAction(int reviewId, HttpServletRequest request, HttpServletResponse response) throws Exception {
        String content = request.getParameter("content");
        String parentCommentId = request.getParameter("parentCommentId");

        CommentDTO comment = new CommentDTO();
        comment.setReviewId(reviewId);

        if (parentCommentId == null || parentCommentId.isEmpty()) {
            comment.setParentCommentId(null);
        } else {
            comment.setParentCommentId(Integer.valueOf(parentCommentId));
        }
        comment.setContent(content);

        HttpSession session = request.getSession(false);
        if (session != null) {
            UserDTO loggedInUser = (UserDTO) session.getAttribute("loggedInUser");
            if (loggedInUser != null) {
                comment.setAuthor(loggedInUser.getId());
            } else {
                comment.setAuthor("익명");
            }
        } else {
            comment.setAuthor("익명");
        }

        commentDAO.insertComment(comment);
        response.sendRedirect("reviewDetail?review_id=" + reviewId);
    }


}
