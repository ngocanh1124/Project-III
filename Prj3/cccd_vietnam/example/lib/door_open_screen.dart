import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Màn hình hiển thị ổ khóa mở — dùng chung cho face match & remote unlock
class DoorOpenScreen extends StatefulWidget {
  final String? label; // tên người / nguồn lệnh (tuỳ chọn)

  const DoorOpenScreen({Key? key, this.label}) : super(key: key);

  @override
  State<DoorOpenScreen> createState() => _DoorOpenScreenState();
}

class _DoorOpenScreenState extends State<DoorOpenScreen>
    with SingleTickerProviderStateMixin {
  late AnimationController _ctrl;
  late Animation<double> _scaleAnim;
  late Animation<double> _fadeAnim;

  @override
  void initState() {
    super.initState();
    HapticFeedback.heavyImpact();

    _ctrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 600),
    );

    _scaleAnim = CurvedAnimation(parent: _ctrl, curve: Curves.elasticOut);
    _fadeAnim  = CurvedAnimation(parent: _ctrl, curve: Curves.easeIn);

    _ctrl.forward();

    // Tự động quay về sau 3 giây
    Future.delayed(const Duration(seconds: 3), () {
      if (mounted) Navigator.of(context).popUntil((route) => route.isFirst);
    });
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black.withOpacity(0.85),
      body: FadeTransition(
        opacity: _fadeAnim,
        child: Center(
          child: ScaleTransition(
            scale: _scaleAnim,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 140,
                  height: 140,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: Colors.green.shade600,
                  ),
                  child: const Icon(
                    Icons.lock_open_rounded,
                    color: Colors.white,
                    size: 80,
                  ),
                ),
                const SizedBox(height: 24),
                const Text(
                  'MỞ CỬA',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 24,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

