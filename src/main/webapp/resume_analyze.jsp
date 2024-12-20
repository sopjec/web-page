<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <jsp:include page="checkSession.jsp"/>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="stylesheet" href="/layout.css"> <!-- 올바른 경로 설정 -->
    <title>자기소개서 분석</title>
    <style>
        .auth-links {
            display: flex;
            gap: 10px;
        }
        .auth-links a {
            text-decoration: none;
            padding: 10px 15px;
            border: 1px solid #ddd;
            border-radius: 4px;
            color: #333;
        }
        .auth-links a:hover {
            background-color: #f0f0f0;
        }
        .box-container {
            display: flex;
            gap: 20px;
        }
        .input-section, .analysis-section {
            flex: 1;
            padding: 20px;
            background-color: white;
            border: 1px solid #ddd;
            border-radius: 4px;
        }
        .input-section h3, .analysis-section h3 {
            margin-top: 0;
            font-size: 18px;
        }
        .input-section textarea, .analysis-section textarea {
            width: 100%;
            height: 300px;
            padding: 10px;
            font-size: 14px;
            border: 1px solid #ddd;
            border-radius: 4px;
            resize: none;
            box-sizing: border-box;
        }
        .input-section .character-count {
            font-size: 12px;
            color: #777;
            text-align: right;
            margin-top: 5px;
        }
        .analyze-button {
            padding: 8px 15px;
            background-color: #333;
            color: white;
            border: none;
            border-radius: 4px;
            cursor: pointer;
            font-size: 14px;
            align-self: center;
        }
        .analyze-button:hover {
            background-color: black;
        }
    </style>

</head>

<body>


<jsp:include page="header.jsp"/>


<div class="container">
        <div class="sidebar">
            <ul>
                <li><a href="#" onclick="checkSessionAndNavigate('resume.jsp'); return false;">자기소개서 등록</a></li>
                <li><a href="#" onclick="checkSessionAndNavigate('resume_view'); return false;">자기소개서 조회</a></li>
                <li><a href="#" onclick="checkSessionAndNavigate('resume_analyze.jsp'); return false;">자기소개서 분석</a></li>
            </ul>
        </div>

        <div class="content">
            <div class="box-container">
                <!-- 변경 전 입력 섹션 -->
                <div class="input-section">
                    <h3>분석 전</h3>
                    <textarea id="coverLetterInput" placeholder="자기소개서를 입력하세요."></textarea>
                    <div class="character-count" id="charCount">0/2000</div>
                </div>

                <!-- 변경 후 분석 결과 섹션 -->
                <div class="analysis-section">
                    <h3>분석 후</h3>
                    <textarea id="analysisOutput" placeholder="분석 결과가 여기에 표시됩니다." readonly></textarea>
                </div>
            </div>

            <!-- 분석하기 버튼 -->
            <button class="analyze-button" id="analyze-button" onclick="analyzeCoverLetter()">분석하기</button>
        </div>
    </div>

    <script>
        const textarea = document.getElementById("coverLetterInput");
        const charCount = document.getElementById("charCount");
        document.getElementById("analyze-button").addEventListener("click", () => {
            const textarea = document.getElementById("coverLetterInput");
            const answerText = textarea.value;

            console.log("Answer:", answerText);

            fetch('/aiCoaching', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json; charset=UTF-8'
                },
                body: JSON.stringify({ text: answerText }) // 입력 텍스트 전송
            })
                .then(response => response.json())
                .then(data => {
                    if (data && data.replacedText) {
                        console.log("Response Text:", data.replacedText);

                        // Result Box에 텍스트 업데이트
                        const resultBox = document.getElementById("analysisOutput");;
                        resultBox.textContent = data.replacedText;
                    } else {
                        alert('AI 코칭 중 오류가 발생했습니다.');
                    }
                })
                .catch(error => {
                    console.error("Error:", error);
                    alert('AI 코칭 교체 요청 실패.resume_analyze.jsp');
                });
        });

        // 글자 수 카운트 업데이트
        textarea.addEventListener("input", () => {
            charCount.textContent = `${textarea.value.length}/2000`;
        });


    </script>
</body>
</html>
