# Face Recognition Attendance System - React Dashboard

A modern React dashboard for managing the face recognition attendance system with real-time statistics, employee management, and reporting.

## Features

- **Real-time Dashboard**: View live attendance statistics and recent records
- **Employee Management**: CRUD operations with Excel import/export
- **Device Management**: Monitor connected devices and remote unlock functionality
- **Attendance History**: Filter and view detailed attendance logs
- **Reports**: Generate and export attendance reports
- **Test Data**: Populate sample data for demo/testing purposes
- **Responsive Design**: Works on desktop and tablet devices

## Prerequisites

- Node.js >= 14.0.0
- npm >= 6.0.0
- Backend API running on http://localhost:8080

## Installation

1. Install dependencies:
```bash
npm install
```

2. Configure environment variables in `.env`:
```
REACT_APP_API_URL=http://localhost:8080/api/v2
REACT_APP_ENV=development
```

## Development

Start the development server:
```bash
npm start
```

The app will open at http://localhost:3000

## Building for Production

```bash
npm run build
```

This creates an optimized production build in the `build` folder.

## Project Structure

```
dashboard-react/
├── public/                 # Static files
├── src/
│   ├── components/        # Reusable UI components
│   │   ├── Sidebar.js
│   │   ├── TopBar.js
│   │   └── *.css
│   ├── pages/            # Page components
│   │   ├── Dashboard.js
│   │   ├── AttendanceHistory.js
│   │   ├── EmployeeManagement.js
│   │   ├── DeviceManagement.js
│   │   ├── Reports.js
│   │   ├── TestData.js
│   │   └── Settings.js
│   ├── services/         # API services
│   │   └── apiService.js # Axios API client
│   ├── hooks/           # Custom React hooks
│   ├── utils/           # Utility functions
│   ├── App.js          # Main app component
│   ├── App.css         # Global styles
│   ├── index.js        # App entry point
│   └── index.css       # Global CSS
├── package.json
├── .env                # Environment variables
└── README.md
```

## API Endpoints

The dashboard connects to the backend API at `http://localhost:8080/api/v2`:

### Attendance
- `POST /attendance/record` - Record attendance
- `GET /attendance/history` - Get attendance history
- `GET /attendance/dashboard/stats` - Get dashboard statistics
- `GET /attendance/devices/{deviceCode}/stats` - Get device stats
- `POST /attendance/remote-unlock/{deviceCode}` - Remote unlock door

### Employees
- `GET /employees` - List employees (paginated)
- `GET /employees/{cccd}` - Get single employee
- `POST /employees` - Create employee
- `PUT /employees/{cccd}` - Update employee
- `DELETE /employees/{cccd}` - Delete employee
- `POST /employees/import` - Import from Excel
- `GET /employees/export` - Export to Excel

### Test Data
- `POST /test/populate-all` - Populate sample data
- `DELETE /test/clear-all` - Clear all data
- `GET /test/stats` - Get data statistics

## Usage

### Dashboard
View real-time statistics including total attempts, success/denied, success rate, active employees, and connected devices.

### Employee Management
- View all employees with pagination
- Create new employees with validation
- Edit employee information
- Delete employees
- Import employees from Excel file
- Export employees to Excel

### Attendance History
- View all attendance records
- Filter by CCCD or device code
- Filter by date range
- Export filtered results

### Device Management
- View all connected devices and their status
- Send remote unlock commands to devices
- Monitor device statistics

### Reports
- Generate attendance reports by date range
- Export to Excel format

### Test Data
- Populate sample data (50 employees, 6 devices, permissions, 200 attendance records)
- Clear all data
- View current database statistics

## Performance Optimizations

- Dashboard auto-refreshes every 30 seconds
- Pagination for large datasets
- Lazy loading of components
- Optimized chart rendering with Chart.js

## Browser Support

- Chrome (latest)
- Firefox (latest)
- Safari (latest)
- Edge (latest)

## Troubleshooting

### API Connection Errors
1. Ensure backend is running on http://localhost:8080
2. Check `.env` file for correct `REACT_APP_API_URL`
3. Verify CORS is enabled on backend

### Build Issues
```bash
rm -rf node_modules package-lock.json
npm install
```

### Port Already in Use
Change the port by setting environment variable:
```bash
PORT=3001 npm start
```

## Development Guide

### Adding a New Page
1. Create component in `src/pages/NewPage.js`
2. Add route in `App.js`
3. Add navigation link in `Sidebar.js`

### API Calls
Use the `apiService` from `src/services/apiService.js`:
```javascript
import { attendanceService } from '../services/apiService';

const data = await attendanceService.getDashboardStats();
```

## License

This project is part of the Face Recognition Attendance System.

## Support

For issues or questions, please refer to the main project documentation.
