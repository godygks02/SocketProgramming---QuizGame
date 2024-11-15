package QuizGame;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
public class QuizClient {

    private BufferedReader in;
    private BufferedWriter out;
    private Socket socket;
    public String studenetID;
    public String studentName;

    public QuizClient() {
        try {
            // server_info.dat 파일에서 ip, port 불러오기
            String[] serverConfig = readServerConfig("./client_database/server_info.dat");
            String serverAddress = serverConfig[0];
            int port = Integer.parseInt(serverConfig[1]);

            // 서버 연결 설정
            socket = new Socket(serverAddress, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            // 연결 완료 메시지
            String connection_complete = getRequest();
            System.out.println(connection_complete);

            // 첫 페이지 GUI
            new InfoPage(this);
            
        } catch (IOException e) {
            System.out.println("Connection error: " + e.getMessage());
        }
    }

    public String getRequest() { // 서버가 보낸 메시지 받기
        String request;
        System.out.println("Waiting for request...");
        try {
            request = in.readLine();
            if(request == null) {
                System.out.println("ERROR: Request is null");
                closeConnection();
            }
            System.out.println("Get Message: " + request);
        } catch(IOException e) {
            System.out.println("GetRequest error: " + e.getMessage());
            return null;
        }
        return request;
    }

    public void sendMsg(String msg) { // 서버로 메시지 전송
        try {
            out.write(msg + "\n"); 
            out.flush();
            System.out.println("Send Message: " + msg);
        } catch(IOException e) {
            System.out.println("SEND error: " + e.getMessage());
        }
    }

    public int handleRequest(String msg ) { // 서버에게 받은 메시지 처리
        System.out.println("HandleRequest: " + msg);
        // SEND method. 서버에서 보낸 메세지 수신
        if(msg.startsWith("SEND")) {
            String[] method = msg.split(" ");
            System.out.println("method[0]: "+ method[0]);
            System.out.println("method[1]: " + method[1]);
            if(method[1].startsWith("ERROR[DUPLICATED_ID]")) { // 학번이 중복되었다는 메시지
                System.out.println("IS DUPLICATED ID!");
                return 1;
            }
            else if(method[1].startsWith("STARTQUIZ[]")) { // 학번 이름 확인 완료 후 퀴즈 시작하라는 메시지
                //퀴즈 요청
            }
            else if(method[1].startsWith("QUIZ[")) { // 퀴즈 메시지
                int beginIndex = method[1].indexOf("[") + 1;
                int endIndex = method[1].indexOf("]");
                // 한 문자열로 되어있는 quiz를 원래 형태로 변환
                String quiz = method[1].substring(beginIndex, endIndex).replace("\\n","\n").replace("^"," ");
                System.out.println("Quiz content: " + quiz);
                //quiz page에 띄우기
                new QuizPage(quiz, this);
            }
            else if(method[1].startsWith("CHECK[")) { // 학번 이름 확인 완료 후 퀴즈 시작하라는 메시지
                int beginIndex = method[1].indexOf("[") + 1;
                int endIndex = method[1].indexOf("]");
                String check = method[1].substring(beginIndex, endIndex); // 정답 여부
                //check page에 띄우기
                new CheckPage(check, this);
            }
            else if(method[1].startsWith("ENDQUIZ[]")) { // 퀴즈가 끝났다는 메시지
                //점수 요청
                sendMsg("GET SCORE[]");
                return -1;
            }
            else if(method[1].startsWith("SCORE[")) { // 최종 점수 메시지
                int beginIndex = method[1].indexOf("[") + 1;
                int endIndex = method[1].indexOf("]");
                int score = Integer.parseInt(method[1].substring(beginIndex, endIndex));
                return score;
            }
        }

        return 0;

    }

    private String[] readServerConfig(String filePath) { //server_info.dat 읽고 서버 ip, port 가져오기
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String ip = br.readLine();
            String port = br.readLine();
            return new String[]{ip, port};
        } catch (IOException e) {
            System.out.println("Error reading server configuration: " + e.getMessage());
            return new String[]{"localhost", "8888"}; // 기본값 설정
        }

    
    }    

    public int sendStudentInfo(String studentID, String studentName) { //학생 학번, 이름 전송
        sendMsg("PUT STUDENT_ID[" + studentID +"]");
        sendMsg("PUT STUDENT_NAME[" + studentName +"]");
        String req = getRequest(); // 학번이 중복인지 확인하는 메시지

        return handleRequest(req);
    }

    public void closeConnection() { // 연결 종료
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            System.out.println("Error closing connection: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        new QuizClient();  // 코드 실행
    }
}

class InfoPage {
    // 변수 선언
    private String studentId;
    private String studentName;

    // GUI 컴포넌트
    private JFrame frame;
    private JTextField idField;
    private JTextField nameField;
    private JButton submitButton;

    public InfoPage(QuizClient client) {
        System.out.println("View InfoPage");
        // JFrame 설정
        frame = new JFrame("학생 정보 입력");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(300, 150);

        // 부모 JPanel 설정 (기본 FlowLayout)
        JPanel parentPanel = new JPanel();
        parentPanel.setLayout(new BorderLayout());

        // 학번 입력 필드
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new FlowLayout());
        JLabel idLabel = new JLabel("학번:");
        idField = new JTextField(15);
        topPanel.add(idLabel);
        topPanel.add(idField);

        // 이름 입력 필드
        JPanel midPanel = new JPanel();
        midPanel.setLayout(new FlowLayout());
        JLabel nameLabel = new JLabel("이름:");
        nameField = new JTextField(15);
        midPanel.add(nameLabel);
        midPanel.add(nameField);

        // 제출 버튼
        JPanel botPanel = new JPanel();
        botPanel.setLayout(new FlowLayout());
        submitButton = new JButton("퀴즈 시작");
        botPanel.add(submitButton);

        // 제출 버튼 클릭 이벤트 처리
        submitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // 입력 값 저장
                studentId = idField.getText();
                studentName = nameField.getText();

                if (!studentId.isEmpty() && !studentName.isEmpty()) {  
                    if(client.sendStudentInfo(studentId, studentName) == 1) { // 학번이 이미 grade.dat에 존재하는 경우
                        JOptionPane.showMessageDialog(frame, "이미 존재하는 학번입니다.");
                        idField.setText("");
                        nameField.setText("");
                        return;
                    }
    
                    System.out.println("학번: " + studentId);
                    System.out.println("이름: " + studentName);  

                    client.studenetID = studentId;
                    client.studentName = studentName;                
                    frame.dispose(); // 첫 화면 종료
                    client.sendMsg("GET QUIZ[]");
                    client.handleRequest(client.getRequest());
                } else {
                    JOptionPane.showMessageDialog(frame, "학번과 이름을 입력하세요!");
                }
            }
        });

        // 컴포넌트들을 부모 패널에 추가
        parentPanel.add(topPanel, BorderLayout.NORTH);
        parentPanel.add(midPanel, BorderLayout.CENTER);
        parentPanel.add(botPanel, BorderLayout.SOUTH);

        // 창 사이즈 고정
        frame.setResizable(false);

        // 프레임에 패널 추가
        frame.add(parentPanel);

        // 화면 중앙에 창을 띄우기 위한 설정
        frame.setLocationRelativeTo(null);

        // 프레임 보이기
        frame.setVisible(true);
    }

}

class QuizPage {

    private JFrame frame;
    private JTextArea questionPage;
    private JTextField answerPage;
    private JButton submitButton;

    public QuizPage(String question, QuizClient client) {
        System.out.println("View QuizPage");
        if (frame != null) {
            frame.dispose();
        }


        frame = new JFrame("QUIZ(" + client.studenetID + " - " + client.studentName + ")");

        // 부모 JPanel 설정 (기본 FlowLayout)
        JPanel parentPanel = new JPanel();
        parentPanel.setLayout(new BorderLayout());

        // 퀴즈 화면 추가
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new FlowLayout());

        // JTextArea 초기화 후, 질문 설정
        questionPage = new JTextArea(20, 50); // 줄 수와 열 수 설정
        questionPage.setEditable(false); // 수정 불가능하게 설정
        questionPage.setText(question); // 텍스트 설정
        questionPage.setWrapStyleWord(true);  // 단어 단위로 줄 바꿈
        questionPage.setLineWrap(true); // 줄바꿈 허용
        questionPage.setCaretPosition(0);     // 텍스트 처음으로 설정
        topPanel.add(questionPage);
        
        // 제출 창 및 버튼 
        JPanel botPanel = new JPanel();
        botPanel.setLayout(new FlowLayout());
        JLabel Label = new JLabel("정답:");
        answerPage = new JTextField(15);
        botPanel.add(Label);
        botPanel.add(answerPage);
        submitButton = new JButton("제출");
        botPanel.add(submitButton);

        // 제출 버튼 클릭 이벤트 처리
        submitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String answer = answerPage.getText().replace(" ","^");
                if (!answer.isEmpty()) {
                    frame.dispose();  // 현재 창 닫기
                    client.sendMsg("GET CHECK[" + answer + "]");  // 서버로 답을 전송
                    client.handleRequest(client.getRequest());
                } else {
                    JOptionPane.showMessageDialog(frame, "답을 입력하세요!");
                }
            }
        });

        frame.setSize(1000, 700);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        // 창 닫을 때 이벤트 처리
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // 경고창 띄우기
                int option = JOptionPane.showConfirmDialog(frame, 
                    "퀴즈 중간에 종료하면 0점 처리 됩니다. 종료하시겠습니까?", 
                    "종료 확인", 
                    JOptionPane.YES_NO_OPTION, 
                    JOptionPane.WARNING_MESSAGE);

                // "예" 버튼 클릭 시 창 닫기
                if (option == JOptionPane.YES_OPTION) {
                    frame.dispose();  // 창 닫기
                }
            }
        });

        parentPanel.add(topPanel, BorderLayout.CENTER);
        parentPanel.add(botPanel, BorderLayout.SOUTH);

        frame.add(parentPanel);

        // 화면 중앙에 창을 띄우기 위한 설정
        frame.setLocationRelativeTo(null);
        // 창 사이즈 고정
        frame.setResizable(false);
        frame.setVisible(true);


    }
 
}

class CheckPage {
    private JFrame frame;
    private JTextArea checkPage;
    private JButton nextButton;

    public CheckPage(String check, QuizClient client) {
        System.out.println("View CheckPage");
        if (frame != null) {
            frame.dispose();
        }

        frame = new JFrame("QUIZ(" + client.studenetID + " - " + client.studentName + ")");

        // 부모 JPanel 설정 (기본 FlowLayout)
        JPanel parentPanel = new JPanel();
        parentPanel.setLayout(new BorderLayout());

        // 퀴즈 화면 추가
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new FlowLayout());

        // JTextArea 초기화 후, 질문 설정
        checkPage = new JTextArea(20, 50); // 줄 수와 열 수 설정
        checkPage.setEditable(false); // 수정 불가능하게 설정
        checkPage.setText(check); // 텍스트 설정
        topPanel.add(checkPage);
        
        // 제출 창 및 버튼 
        JPanel botPanel = new JPanel();
        botPanel.setLayout(new FlowLayout());
        nextButton = new JButton("다음");
        botPanel.add(nextButton);

        // 제출 버튼 클릭 이벤트 처리
        nextButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.dispose();  // 현재 창 닫기
                client.sendMsg("GET QUIZ[]");  // 다음 퀴즈 요청
                if(client.handleRequest(client.getRequest()) == -1) { // 퀴즈가 끝났다는 메시지를 받았을 때
                    int score = client.handleRequest(client.getRequest()); // 점수 요청 및 받기
                    new ScorePage(score, client); // 점수 페이지 출력
                }
            }
        });

        frame.setSize(1000, 700);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        // 창 닫을 때 이벤트 처리
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // 경고창 띄우기
                int option = JOptionPane.showConfirmDialog(frame, 
                    "퀴즈 중간에 종료하면 0점 처리 됩니다. 종료하시겠습니까?", 
                    "종료 확인", 
                    JOptionPane.YES_NO_OPTION, 
                    JOptionPane.WARNING_MESSAGE);

                // "예" 버튼 클릭 시 창 닫기
                if (option == JOptionPane.YES_OPTION) {
                    frame.dispose();  // 창 닫기
                }
            }
        });

        parentPanel.add(topPanel, BorderLayout.CENTER);
        parentPanel.add(botPanel, BorderLayout.SOUTH);

        frame.add(parentPanel);

        // 화면 중앙에 창을 띄우기 위한 설정
        frame.setLocationRelativeTo(null);
        // 창 사이즈 고정
        frame.setResizable(false);
        frame.setVisible(true);


    }
}

class ScorePage {
    private JFrame frame;
    private JTextArea scorePage;
    private JButton Button;

    public ScorePage(int score, QuizClient client) {
        System.out.println("View ScorePage");
        if (frame != null) {
            frame.dispose();
        }

        frame = new JFrame("QUIZ(" + client.studenetID + " - " + client.studentName + ")");

        // 부모 JPanel 설정 (기본 FlowLayout)
        JPanel parentPanel = new JPanel();
        parentPanel.setLayout(new BorderLayout());

        // 정답 화면 추가
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new FlowLayout());

        // JTextArea 초기화 후, 질문 설정
        scorePage = new JTextArea(5, 20); // 줄 수와 열 수 설정s
        scorePage.setEditable(false); // 수정 불가능하게 설정
        scorePage.setText("당신의 점수: " + score); // 텍스트 설정
        topPanel.add(scorePage);
        
        // 확인 버튼 
        JPanel botPanel = new JPanel();
        botPanel.setLayout(new FlowLayout());
        Button = new JButton("종료");
        botPanel.add(Button);

        // 제출 버튼 클릭 이벤트 처리
        Button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) { // 종료
                client.closeConnection();
                System.exit(0);
            }
        });

        frame.setSize(400, 300);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        parentPanel.add(topPanel, BorderLayout.CENTER);
        parentPanel.add(botPanel, BorderLayout.SOUTH);

        frame.add(parentPanel);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

    }
}