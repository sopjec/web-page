let mediaRecorder;
let recordedChunks = [];
let webcamStream;
let expressionInterval;
let questions = [];
let currentQuestionIndex = 0;
let isFirstQuestionDisplayed = false;
let isSpeaking = false;
let recognition; // 음성 인식 객체

// URL에서 resumeId 가져오기
const urlParams = new URLSearchParams(window.location.search);
const resumeId = urlParams.get('resumeId');

// 텍스트 음성 읽기 함수
function readTextAloud(text, onEndCallback) {
    if (!window.speechSynthesis) {
        console.error('이 브라우저는 Web Speech API를 지원하지 않습니다.');
        return;
    }

    if (isSpeaking) {
        window.speechSynthesis.cancel();
    }

    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = 'ko-KR';
    utterance.rate = 1;
    utterance.pitch = 1;

    utterance.onstart = () => {
        isSpeaking = true;
    };

    utterance.onend = () => {
        isSpeaking = false;
        if (onEndCallback) onEndCallback();
    };

    speechSynthesis.speak(utterance);
}

// Face-api.js 모델 로드
async function loadModels() {
    try {
        console.log('Face-api.js 모델 로드 시작');
        await faceapi.nets.tinyFaceDetector.loadFromUri('/models');
        console.log('tinyFaceDetector 모델 로드 성공');
        await faceapi.nets.faceExpressionNet.loadFromUri('/models');
        console.log('faceExpressionNet 모델 로드 성공');
    } catch (error) {
        console.error('Face-api.js 모델 로드 중 오류:', error);
    }
}

// 실시간 감정 분석
async function analyzeExpressions() {
    const video = document.getElementById('user-webcam');
    const expressionOutput = document.getElementById('user-expression-output'); // 웹캠 아래 텍스트 출력

    expressionInterval = setInterval(async () => {
        try {
            const detections = await faceapi
                .detectSingleFace(video, new faceapi.TinyFaceDetectorOptions())
                .withFaceExpressions();

            if (detections) {
                const expressions = detections.expressions;
                const dominantExpression = Object.keys(expressions).reduce((a, b) =>
                    expressions[a] > expressions[b] ? a : b
                );

                expressionOutput.innerHTML = `현재 표정: ${dominantExpression} (${(expressions[dominantExpression] * 100).toFixed(2)}%)`;
                console.log('감정 분석 결과:', expressions);
            } else {
                expressionOutput.innerHTML = '얼굴이 감지되지 않았습니다.';
                console.warn('얼굴이 감지되지 않았습니다.');
            }
        } catch (error) {
            console.error('감정 분석 중 오류:', error);
        }
    }, 500); // 500ms 간격으로 분석
}

// 음성 인식 초기화
function initSpeechRecognition() {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRecognition) {
        console.error('이 브라우저는 SpeechRecognition API를 지원하지 않습니다.');
        return null;
    }

    const recognizer = new SpeechRecognition();
    recognizer.lang = 'ko-KR';
    recognizer.interimResults = true;
    recognizer.continuous = true;
    return recognizer;
}

// 음성 인식 시작
function startSpeechRecognition() {
    const userTextOutput = document.getElementById('user-text-output');
    recognition = initSpeechRecognition();

    if (!recognition) return;

    recognition.start();
    console.log('음성 인식 시작');

    recognition.onresult = (event) => {
        let transcript = '';
        for (let i = event.resultIndex; i < event.results.length; ++i) {
            transcript += event.results[i][0].transcript;
        }
        userTextOutput.innerHTML += ' ' + transcript; // 텍스트를 누적하여 출력
        console.log('음성 인식 결과:', transcript);
    };

    recognition.onerror = (event) => {
        console.error('음성 인식 오류:', event.error);
    };

    recognition.onend = () => {
        console.log('음성 인식 종료');
    };
}

// 질문 생성 및 음성 출력
async function generateQuestionAndSpeak() {
    const response = await fetch('/api/generate-question', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ resumeId }),
    });

    if (response.ok) {
        const data = await response.json();
        questions = data.question.split('\n').filter(q => q.trim() !== '').map(q => q.replace(/^\d+\.\s*/, ''));
        currentQuestionIndex = 0;

        if (questions.length > 0) {
            const question = `질문 ${currentQuestionIndex + 1}: ${questions[currentQuestionIndex]}`;
            document.getElementById('interviewer-text-output').innerHTML = question;
            readTextAloud(question, startSpeechRecognition);
        } else {
            document.getElementById('interviewer-text-output').innerText = '질문 데이터가 없습니다.';
        }
    } else {
        console.error('서버 오류:', response.statusText);
        document.getElementById('interviewer-text-output').innerText = '질문 생성 중 오류 발생';
    }
}

// 페이지 녹화 시작
async function startPageRecording() {
    try {
        const screenStream = await navigator.mediaDevices.getDisplayMedia({
            video: { cursor: "always" },
            audio: false,
        });

        const micStream = await navigator.mediaDevices.getUserMedia({
            audio: true,
        });

        const combinedStream = new MediaStream([
            ...screenStream.getVideoTracks(),
            ...micStream.getAudioTracks(),
        ]);

        mediaRecorder = new MediaRecorder(combinedStream);
        recordedChunks = [];

        mediaRecorder.ondataavailable = (event) => {
            if (event.data.size > 0) {
                recordedChunks.push(event.data);
            }
        };

        mediaRecorder.onstop = saveRecording;
        mediaRecorder.start();

        // 화면 녹화가 시작된 후 질문 생성 및 음성 출력
        generateQuestionAndSpeak();

        console.log('페이지 녹화가 시작되었습니다.');
    } catch (error) {
        console.error('녹화 시작 중 오류:', error);
    }
}

// 녹화 저장
function saveRecording() {
    if (recordedChunks.length === 0) {
        console.warn('녹화된 데이터가 없습니다.');
        return;
    }

    const blob = new Blob(recordedChunks, { type: 'video/webm' });
    const url = URL.createObjectURL(blob);
    const downloadLink = document.createElement('a');
    downloadLink.href = url;
    downloadLink.download = `recording_${Date.now()}.webm`;
    document.body.appendChild(downloadLink);
    downloadLink.click();
    document.body.removeChild(downloadLink);
    console.log('녹화본 저장 완료');
}

// 면접 시작
async function startInterview() {
    try {
        webcamStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
        document.getElementById('user-webcam').srcObject = webcamStream;

        console.log('웹캠 스트림 연결 성공');
        startPageRecording(); // 녹화 시작과 동시에 질문 생성

        await loadModels(); // 모델 로드
        console.log('감정 분석 시작...');
        analyzeExpressions(); // 감정 분석 시작
    } catch (error) {
        console.error('웹캠 연결 오류:', error);
        alert('웹캠과 마이크 접근 권한을 확인하세요.');
    }
}

// 이벤트 리스너 설정
document.getElementById('start-interview').addEventListener('click', startInterview);

document.getElementById('next-question').addEventListener('click', () => {
    if (currentQuestionIndex + 1 < questions.length) {
        currentQuestionIndex++;
        const question = `질문 ${currentQuestionIndex + 1}: ${questions[currentQuestionIndex]}`;
        document.getElementById('interviewer-text-output').innerHTML = question;
        readTextAloud(question, startSpeechRecognition);
    } else {
        document.getElementById('interviewer-text-output').innerText = '모든 질문이 완료되었습니다.';
        alert('면접이 종료되었습니다.');
    }
});

document.getElementById('stop-recording').addEventListener('click', () => {
    if (mediaRecorder && mediaRecorder.state === 'recording') {
        mediaRecorder.stop();
        console.log('녹화 중지');
    }
    if (webcamStream) {
        webcamStream.getTracks().forEach(track => track.stop());
        console.log('웹캠 스트림 중지');
    }
});