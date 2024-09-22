let currentPage = 1;
const rowsPerPage = 10;

const data = [
  { id: 1, title: "질문 1", userId: "user1", date: "2024-09-01", replies: 0 },
  { id: 2, title: "질문 2", userId: "user2", date: "2024-09-02", replies: 1 },
  // ... (여기에 더 많은 데이터 추가)
  {
    id: 20,
    title: "질문 20",
    userId: "user20",
    date: "2024-09-20",
    replies: 5,
  },
];

function renderTable() {
  const tbody = document.getElementById("table-body");
  tbody.innerHTML = "";

  const start = (currentPage - 1) * rowsPerPage;
  const end = start + rowsPerPage;
  const pageData = data.slice(start, end);

  pageData.forEach((item) => {
    const row = `
            <tr>
                <td>${item.id}</td>
                <td>${item.title}</td>
                <td>${item.userId}</td>
                <td>${item.date}</td>
                <td>${item.replies}</td>
            </tr>
        `;
    tbody.innerHTML += row;
  });

  updatePagination();
}

function updatePagination() {
  const totalPages = Math.ceil(data.length / rowsPerPage);

  document.getElementById("page1").classList.remove("active");
  document.getElementById("page2").classList.remove("active");

  if (currentPage === 1) {
    document.getElementById("prev").style.display = "none";
  } else {
    document.getElementById("prev").style.display = "inline";
  }

  if (currentPage === totalPages) {
    document.getElementById("next").style.display = "none";
  } else {
    document.getElementById("next").style.display = "inline";
  }

  if (currentPage === 1) {
    document.getElementById("page1").classList.add("active");
  } else {
    document.getElementById("page2").classList.add("active");
  }
}

function changePage(direction) {
  const totalPages = Math.ceil(data.length / rowsPerPage);

  if (direction === -1 && currentPage > 1) {
    currentPage--;
  } else if (direction === 1 && currentPage < totalPages) {
    currentPage++;
  }

  renderTable();
}

// 초기 렌더링
renderTable();
