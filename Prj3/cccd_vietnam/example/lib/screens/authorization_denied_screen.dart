import 'package:flutter/material.dart';

class AuthorizationDeniedScreen extends StatefulWidget {
  final String? cccdNumber;
  final String? reason;

  const AuthorizationDeniedScreen({
    Key? key,
    this.cccdNumber,
    this.reason,
  }) : super(key: key);

  @override
  State<AuthorizationDeniedScreen> createState() =>
      _AuthorizationDeniedScreenState();
}

class _AuthorizationDeniedScreenState extends State<AuthorizationDeniedScreen> {
  @override
  void initState() {
    super.initState();
    // Auto return after 3 seconds
    Future.delayed(const Duration(seconds: 3), () {
      if (mounted) {
        Navigator.of(context).popUntil((route) => route.isFirst);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Kiểm tra Quyền'),
        elevation: 0,
        automaticallyImplyLeading: false,
      ),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              // Denied Icon
              Container(
                width: 120,
                height: 120,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: Colors.red.withValues(alpha: 0.1),
                ),
                child: const Icon(
                  Icons.cancel,
                  size: 80,
                  color: Colors.red,
                ),
              ),
              const SizedBox(height: 32),

              // Title
              const Text(
                'Không có quyền truy cập',
                style: TextStyle(
                  fontSize: 22,
                  fontWeight: FontWeight.bold,
                  color: Colors.red,
                ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 16),

              // Reason
              if (widget.cccdNumber != null)
                Text(
                  'CCCD: ${widget.cccdNumber}',
                  style: const TextStyle(
                    fontSize: 14,
                    color: Colors.grey,
                  ),
                ),
              const SizedBox(height: 24),

              // Message
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: Colors.orange.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(
                    color: Colors.orange.withValues(alpha: 0.3),
                  ),
                ),
                child: Column(
                  children: [
                    const Text(
                      'Lý do:',
                      style: TextStyle(
                        fontWeight: FontWeight.w600,
                        fontSize: 12,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      widget.reason ??
                          'CCCD này không có quyền vào cửa này.\n'
                          'Vui lòng liên hệ quản trị viên để xin phép.',
                      style: const TextStyle(
                        fontSize: 13,
                        height: 1.6,
                        color: Colors.orange,
                      ),
                      textAlign: TextAlign.center,
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 32),

              // Buttons
              SizedBox(
                width: double.infinity,
                child: ElevatedButton.icon(
                  onPressed: () =>
                      Navigator.of(context).popUntil((route) => route.isFirst),
                  icon: const Icon(Icons.home),
                  label: const Text('Quay về trang chủ'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.grey,
                    padding: const EdgeInsets.symmetric(vertical: 12),
                  ),
                ),
              ),

              const SizedBox(height: 12),

              Text(
                'Quay về trong 3 giây...',
                style: TextStyle(
                  fontSize: 12,
                  color: Colors.grey.withValues(alpha: 0.6),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
